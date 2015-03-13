package io.crate.frameworks.mesos;

import org.apache.mesos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler to launch Docker containers.
 *
 */

public class CrateScheduler implements Scheduler {

    /** Logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateScheduler.class);

    /** Number of instances to run. */
    private final int desiredInstances;
    private int launchedInstances;

    /** List of pending instances. */
    private final List<String> pendingInstances = new ArrayList<>();

    /** List of running instances. */
    private final List<String> runningInstances = new ArrayList<>();

    private final Map<String, String> taskToHostname = new HashMap<>();

    /** List of hosts that run Crate **/
    private final Set<String> crateHosts = new HashSet<>();

    /** Task ID generator. */
    private final AtomicInteger taskIDGenerator = new AtomicInteger();

    /** Constructor. */
    public CrateScheduler(int desiredInstances) {
        this.desiredInstances = desiredInstances;
        this.launchedInstances = 0;
    }

    @Override
    public void registered(SchedulerDriver schedulerDriver, Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {
        LOGGER.info("registered() master={}:{}, framework={}",
                masterInfo.getIp(), masterInfo.getPort(), frameworkID.getValue());
    }

    @Override
    public void reregistered(SchedulerDriver schedulerDriver, Protos.MasterInfo masterInfo) {
        LOGGER.info("reregistered() master={}", masterInfo.getHostname());
    }

    @Override
    public void resourceOffers(SchedulerDriver schedulerDriver, List<Protos.Offer> offers) {
        LOGGER.info("resourceOffers() with {} offers", offers.size());

        for (Protos.Offer offer : offers) {
            List<Protos.TaskInfo> tasks = new ArrayList<>();
            List<Protos.OfferID> offerIDs = new ArrayList<>();

            if (runningInstances.size() + pendingInstances.size() < desiredInstances) {

                if (!crateHosts.add(offer.getHostname())) {
                    schedulerDriver.declineOffer(offer.getId());
                    continue;
                }

                int id = taskIDGenerator.incrementAndGet();

                CrateContainer container = new CrateContainer(id, "mesos",
                        offer.getHostname(),
                        crateHosts);
                Protos.TaskInfo taskInfo = container.taskInfo(offer);
                String taskId = container.taskId().getValue();

                LOGGER.info("Launching task {}", taskId);
                LOGGER.info("launchedInstances {}", launchedInstances);

                pendingInstances.add(taskId);
                taskToHostname.put(taskId, offer.getHostname());
                this.launchedInstances++;
                tasks.add(taskInfo);
                LOGGER.debug("task: {}", taskInfo);
                offerIDs.add(offer.getId());
            }
            Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
            schedulerDriver.launchTasks(offerIDs, tasks, filters);
        }
    }

    @Override
    public void offerRescinded(SchedulerDriver schedulerDriver, Protos.OfferID offerID) {
        LOGGER.info("offerRescinded()");
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus taskStatus) {
        final String taskId = taskStatus.getTaskId().getValue();

        LOGGER.info("statusUpdate() task {} is in state {}: {}",
                taskId, taskStatus.getState(), taskStatus.getMessage());

        switch (taskStatus.getState()) {
            case TASK_RUNNING:
                pendingInstances.remove(taskId);
                runningInstances.add(taskId);
                break;
            case TASK_LOST:
            case TASK_FAILED:
            case TASK_FINISHED:
                pendingInstances.remove(taskId);
                runningInstances.remove(taskId);
                String hostName = taskToHostname.remove(taskId);
                if (hostName != null) {
                    crateHosts.remove(hostName);
                }
                break;
        }

        LOGGER.info("Number of instances: pending={}, running={}",
                pendingInstances.size(), runningInstances.size());
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

