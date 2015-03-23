package io.crate.frameworks.mesos;

import io.crate.frameworks.mesos.config.Configuration;
import io.crate.frameworks.mesos.config.Resources;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.crate.frameworks.mesos.SaneProtos.taskID;


public class CrateScheduler implements Scheduler {

    private class InstancesObserver implements Observer<Integer> {
        private SchedulerDriver driver;

        public InstancesObserver(SchedulerDriver driver) {
            this.driver = driver;
        }

        public void update(Integer data) {
            LOGGER.info("got new desiredInstances value: {}", data);
            if (driver != null) {
                resizeCluster(driver);
            }
        }

        public void driver(SchedulerDriver driver) {
            assert driver != null : "driver must not be null";
            this.driver = driver;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateScheduler.class);

    private final PersistentStateStore stateStore;
    private final Configuration configuration;

    private InstancesObserver instancesObserver = new InstancesObserver(null);
    private CrateInstances crateInstances;
    ArrayList<Protos.TaskStatus> reconcileTasks = new ArrayList<>();

    public CrateScheduler(PersistentStateStore store, Configuration configuration) {
        this.stateStore = store;
        this.configuration = configuration;
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {
        LOGGER.info("Registered framework with frameworkId {}", frameworkID.getValue());
        CrateState state = stateStore.state();

        state.frameworkId(frameworkID.getValue());
        stateStore.save();
        // todo: use instances from state
        crateInstances = state.crateInstances();

        instancesObserver.driver(driver);
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Reregistered framwork. Starting task reconciliation.");
        CrateState state = stateStore.state();
        instancesObserver.driver(driver);
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        LOGGER.info("resourceOffers() with {} offer(s)", offers.size());

        if (!reconcileTasks.isEmpty()) {
            LOGGER.info("declining all offers ... got some reconcile tasks");
            declineAllOffers(driver, offers);
            return;
        }

        CrateState state = stateStore.state();
        int desiredInstances = state.desiredInstances().getValue();

        int toKill = state.crateInstances().size() - desiredInstances;
        if (toKill == 0) {
            LOGGER.debug("Nothing to do ... decline offers!");
            declineAllOffers(driver, offers);
        } else if (toKill >= 0) {
            killInstances(driver, toKill);
            declineAllOffers(driver, offers);
        } else {
            int toSpawn = toKill * -1;
            LOGGER.debug("toSpawn: {}", toSpawn);

            List<Protos.TaskInfo> tasks = new ArrayList<>(toSpawn);
            List<Protos.OfferID> offerIDs = new ArrayList<>(toSpawn);

            for (Protos.Offer offer : offers) {
                if (tasks.size() == toSpawn) {
                    driver.declineOffer(offer.getId());
                    continue;
                }

                if (crateInstances.anyOnHost(offer.getHostname())) {
                    LOGGER.info("got already an instance on {}, rejecting offer {}", offer.getHostname(), offer.getId().getValue());
                    driver.declineOffer(offer.getId());
                    continue;
                }

                if (!Resources.matches(offer.getResourcesList(), configuration)) {
                    LOGGER.info("can't use offer {}; not enough resources", offer.getId().getValue());
                    driver.declineOffer(offer.getId());
                    continue;
                }
                CrateContainer container = new CrateContainer(
                        configuration,
                        offer.getHostname(),
                        crateInstances.hosts()
                );
                Protos.TaskInfo taskInfo = container.taskInfo(offer);

                LOGGER.info("Launching task {}", container.taskId().getValue());

                crateInstances.addInstance(new CrateInstance(
                        container.getHostname(),
                        taskInfo.getTaskId().getValue(),
                        configuration.version()
                ));
                tasks.add(taskInfo);
                LOGGER.debug("task: {}", taskInfo);
                offerIDs.add(offer.getId());
            }

            if (!tasks.isEmpty()) {
                Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
                state.instances(crateInstances);

                stateStore.save();
                driver.launchTasks(offerIDs, tasks, filters);
            }
        }

    }

    private void declineAllOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        LOGGER.debug("decline all offers: {}", offers.size());
        for (Protos.Offer offer : offers) {
            LOGGER.debug("decline: {}", offer.getId());
            driver.declineOffer(offer.getId());
        }
    }

    private void killInstances(SchedulerDriver driver, int toKill) {
        int killed = 0;
        // TODO: need to check cluster state to make sure cluster has enough time to re-balance between kills
        LOGGER.info("Too many instances running. Killing {} tasks", toKill);
        for (CrateInstance crateInstance : crateInstances) {
            if (killed == toKill) {
                break;
            }
            LOGGER.info("Kill task {}", crateInstance.taskId());
            driver.killTask(taskID(crateInstance.taskId()));
            killed++;
        }
    }

    @Override
    public void offerRescinded(SchedulerDriver schedulerDriver, Protos.OfferID offerID) {
        LOGGER.info("Offer rescinded: {}", offerID);
        // if any pending on that offer remove them?
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus taskStatus) {
        final String taskId = taskStatus.getTaskId().getValue();
        LOGGER.info("statusUpdate() {}", taskStatus.getMessage());
        LOGGER.info("{} {}", taskStatus.getState(), taskId);

        if (!reconcileTasks.isEmpty()) {
            for (int i = reconcileTasks.size()-1; i >= 0; i--) {
                if (reconcileTasks.get(i).getTaskId().getValue().equals(taskId)) {
                    LOGGER.debug("remove reconcile task: {}", i, reconcileTasks.get(i));
                    reconcileTasks.remove(i);
                }
            }
            LOGGER.debug("revive offers ...");
            driver.reviveOffers();
        }

        switch (taskStatus.getState()) {
            case TASK_RUNNING:
                LOGGER.debug("update state to running ...");
                crateInstances.setToRunning(taskId);
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                LOGGER.debug("waiting ...");
                break;
            case TASK_LOST:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_FINISHED:
                LOGGER.debug("remove task ...");
                crateInstances.removeTask(taskId);
                break;
            default:
                LOGGER.warn("invalid state");
                break;
        }

        stateStore.state().instances(crateInstances);
        stateStore.save();

        resizeCluster(driver);
    }

    private void resizeCluster(SchedulerDriver driver) {
        int instancesMissing = stateStore.state().desiredInstances().getValue() - crateInstances.size();
        LOGGER.debug("Resize cluster: {}", instancesMissing);
        if (instancesMissing > 0) {
            requestMoreResources(driver, instancesMissing);
        } else if (instancesMissing < 0) {
            killInstances(driver, instancesMissing * -1);
        }
    }

    private void requestMoreResources(SchedulerDriver driver, int instancesMissing) {
        LOGGER.info("asking for more resources for {} more instances", instancesMissing);

        List<Protos.Request> requests = new ArrayList<>(instancesMissing);
        for (int i = 0; i < instancesMissing; i++) {
            requests.add(Protos.Request.newBuilder()
                            .addAllResources(configuration.getAllRequiredResources())
                            .build()
            );
        }
        driver.requestResources(requests);
    }

    @Override
    public void frameworkMessage(SchedulerDriver driver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, byte[] bytes) {
        LOGGER.info("frameworkMessage()");
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        LOGGER.info("disconnected()");
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID slaveID) {
        LOGGER.info("slaveLost()");
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, int i) {
        LOGGER.info("executorLost()");
    }

    @Override
    public void error(SchedulerDriver driver, String s) {
        LOGGER.error("error() {}", s);
    }

    private void reconcileTasks(SchedulerDriver driver) {
        LOGGER.debug("Reconciling tasks ... {}", crateInstances.size());
        if (crateInstances.size() > 0) {
            reconcileTasks = new ArrayList<>(crateInstances.size());
            for (CrateInstance instance : crateInstances) {
                Protos.TaskState state = instance.state() == CrateInstance.State.RUNNING
                        ? Protos.TaskState.TASK_RUNNING
                        : Protos.TaskState.TASK_STARTING;
                LOGGER.debug("taskID {} state={}", instance.taskId(), state);
                Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder();
                builder.setState(state);
                builder.setTaskId(taskID(instance.taskId()));
                reconcileTasks.add(builder.build());
            }
            driver.reconcileTasks(reconcileTasks);
        }
    }

}

