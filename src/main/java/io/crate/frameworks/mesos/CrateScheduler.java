package io.crate.frameworks.mesos;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.state.ZooKeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.crate.frameworks.mesos.SaneProtos.*;


/**
 * Scheduler to launch Docker containers.
 *
 */

public class CrateScheduler implements Scheduler {

    /** Logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateScheduler.class);

    private final CrateState crateState;

    /** Task ID generator. */
    private final AtomicInteger taskIDGenerator = new AtomicInteger();

    private CrateInstances crateInstances;
    private ArrayList<Protos.TaskStatus> reconcileTasks = new ArrayList<>();

    public CrateScheduler(int desiredInstances) {
        crateState = new CrateState(new ZooKeeperState("localhost:2181", 20000, TimeUnit.MILLISECONDS, "/crate-mesos/CrateFramework"));
    }

    @Override
    public void registered(SchedulerDriver schedulerDriver, Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {
        LOGGER.info("registered() master={}:{}, framework={}",
                masterInfo.getIp(), masterInfo.getPort(), frameworkID.getValue());

        crateInstances = crateState.retrieveState();

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
        crateInstances = crateState.retrieveState();

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
            for (int i = 0; i < toKill; i++) {
                // TODO: need to check cluster state to make sure cluster has enough time to re-balance between kills
                LOGGER.info("too many instances running.. kill task");
                driver.killTask(taskID(crateInstances.pop().taskId()));
            }
        } else if (toKill < 0) {
            int toSpawn = toKill * -1;

            List<Protos.TaskInfo> tasks = new ArrayList<>(toSpawn);
            List<Protos.OfferID> offerIDs = new ArrayList<>(toSpawn);

            for (Protos.Offer offer : offers) {
                if (crateInstances.anyOnHost(offer.getHostname())) {
                    LOGGER.info("got already an instance on {}, rejecting offer {}", offer.getHostname(), offer.getId().getValue());
                    driver.declineOffer(offer.getId());
                    continue;
                }

                int id = taskIDGenerator.incrementAndGet();
                CrateContainer container = new CrateContainer(id, "mesos", offer.getHostname(), crateInstances.hosts());
                Protos.TaskInfo taskInfo = container.taskInfo(offer);

                LOGGER.info("Launching task {}", container.taskId().getValue());

                crateInstances.addInstance(new CrateInstance(container, taskInfo));
                tasks.add(taskInfo);
                LOGGER.debug("task: {}", taskInfo);
                offerIDs.add(offer.getId());
            }

            if (!tasks.isEmpty()) {
                Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
                driver.launchTasks(offerIDs, tasks, filters);
            }
        }

        crateState.storeState(crateInstances);
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
        crateState.storeState(crateInstances);

        int instancesMissing = crateState.desiredInstances() - crateInstances.size();
        if (instancesMissing > 0) {
            requestMoreResources(driver, instancesMissing);
        }
    }

    private void requestMoreResources(SchedulerDriver driver, int instancesMissing) {
        LOGGER.info("asking for more resources for {} more instances", instancesMissing);
        driver.reviveOffers();

        List<Protos.Request> requests = new ArrayList<>(instancesMissing);
        for (int i = 0; i < instancesMissing; i++) {
            Protos.Request.Builder builder = Protos.Request.newBuilder();
            // TODO: how many resources to use per instance should be configurable
            builder.addResources(cpus(1));
            builder.addResources(mem(200));
            builder.addResources(scalarResource("ports", 4200, null));
            builder.addResources(scalarResource("ports", 4300, null));
            requests.add(builder.build());
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

