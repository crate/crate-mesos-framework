package io.crate.frameworks.mesos;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.crate.frameworks.mesos.SaneProtos.taskID;


/**
 * Scheduler to launch Docker containers.
 *
 */

public class CrateScheduler implements Scheduler {

    /** Logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateScheduler.class);

    private final CrateState crateState;
    private final ResourceConfiguration resourceConfiguration;

    private CrateInstances crateInstances;
    ArrayList<Protos.TaskStatus> reconcileTasks = new ArrayList<>();

    public CrateScheduler(CrateState crateState, ResourceConfiguration resourceConfiguration) {
        this.crateState = crateState;
        this.resourceConfiguration = resourceConfiguration;
    }

    @Override
    public void registered(SchedulerDriver schedulerDriver, Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {
        LOGGER.info("registered() master={}:{}, framework={}",
                masterInfo.getIp(), masterInfo.getPort(), frameworkID.getValue());

        crateState.frameworkID(frameworkID.getValue());
        crateInstances = crateState.crateInstances();

        if (crateInstances.size() > 0) {
            reconcileTasks = new ArrayList<>(crateInstances.size());
            for (CrateInstance crateInstance : crateInstances) {
                Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder();
                builder.setState(crateInstance.state() == CrateInstance.State.RUNNING
                        ? Protos.TaskState.TASK_RUNNING
                        : Protos.TaskState.TASK_STARTING);
                builder.setTaskId(taskID(crateInstance.taskId()));
                reconcileTasks.add(builder.build());
            }
            schedulerDriver.reconcileTasks(reconcileTasks);
        }
    }

    @Override
    public void reregistered(SchedulerDriver schedulerDriver, Protos.MasterInfo masterInfo) {
        LOGGER.info("reregistered() master={}", masterInfo.getHostname());
        crateInstances = crateState.crateInstances();

        // TODO: also run reconcile
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        LOGGER.info("resourceOffers() with {} offers", offers.size());

        if (!reconcileTasks.isEmpty()) {
            LOGGER.info("declining all offers.. got some reconcile tasks");
            for (Protos.Offer offer : offers) {
                driver.declineOffer(offer.getId());
            }
            return;
        }

        int desiredInstances = crateState.desiredInstances();

        int toKill = crateInstances.size() - desiredInstances;
        if (toKill > 0) {
            killInstances(driver, toKill);
        } else if (toKill < 0) {
            int toSpawn = toKill * -1;

            List<Protos.TaskInfo> tasks = new ArrayList<>(toSpawn);
            List<Protos.OfferID> offerIDs = new ArrayList<>(toSpawn);

            for (Protos.Offer offer : offers) {
                if (tasks.size() == toSpawn) {
                    break;
                }

                if (crateInstances.anyOnHost(offer.getHostname())) {
                    LOGGER.info("got already an instance on {}, rejecting offer {}", offer.getHostname(), offer.getId().getValue());
                    driver.declineOffer(offer.getId());
                    continue;
                }

                if (!resourceConfiguration.matches(offer.getResourcesList())) {
                    LOGGER.info("can't use offer {}; not enough resources", offer.getId().getValue());
                    continue;
                }

                CrateContainer container = new CrateContainer(
                        "mesos", offer.getHostname(), crateInstances.hosts(), resourceConfiguration);
                Protos.TaskInfo taskInfo = container.taskInfo(offer);

                LOGGER.info("Launching task {}", container.taskId().getValue());

                crateInstances.addInstance(new CrateInstance(container.getHostname(), taskInfo.getTaskId().getValue()));
                tasks.add(taskInfo);
                LOGGER.debug("task: {}", taskInfo);
                offerIDs.add(offer.getId());
            }

            if (!tasks.isEmpty()) {
                Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
                crateState.instances(crateInstances);
                driver.launchTasks(offerIDs, tasks, filters);
            }
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
            driver.killTask(taskID(crateInstance.taskId()));
            killed++;
        }
    }

    @Override
    public void offerRescinded(SchedulerDriver schedulerDriver, Protos.OfferID offerID) {
        LOGGER.info("offerRescinded()");
        // if any pending on that offer remove them?
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus taskStatus) {
        final String taskId = taskStatus.getTaskId().getValue();
        LOGGER.info("statusUpdate() task {} is in state {}: {}",
                taskId, taskStatus.getState(), taskStatus.getMessage());

        if (!reconcileTasks.isEmpty()) {
            for (int i = 0; i < reconcileTasks.size(); i++) {
                if (reconcileTasks.get(i).getTaskId().getValue().equals(taskId)) {
                    reconcileTasks.remove(i);
                }
            }
            driver.reviveOffers();
        }

        switch (taskStatus.getState()) {
            case TASK_RUNNING:
                crateInstances.setToRunning(taskId);
                break;
            case TASK_LOST:
            case TASK_FAILED:
            case TASK_FINISHED:
                crateInstances.removeTask(taskId);
                break;
        }
        crateState.instances(crateInstances);

        int instancesMissing = crateState.desiredInstances() - crateInstances.size();
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
                    .addAllResources(resourceConfiguration.getAllRequiredResources())
                    .build()
            );
        }
        driver.requestResources(requests);
    }

    @Override
    public void frameworkMessage(SchedulerDriver schedulerDriver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, byte[] bytes) {
        LOGGER.info("frameworkMessage()");
    }

    @Override
    public void disconnected(SchedulerDriver schedulerDriver) {
        LOGGER.info("disconnected()");
    }

    @Override
    public void slaveLost(SchedulerDriver schedulerDriver, Protos.SlaveID slaveID) {
        LOGGER.info("slaveLost()");
    }

    @Override
    public void executorLost(SchedulerDriver schedulerDriver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, int i) {
        LOGGER.info("executorLost()");
    }

    @Override
    public void error(SchedulerDriver schedulerDriver, String s) {
        LOGGER.error("error() {}", s);
    }

}

