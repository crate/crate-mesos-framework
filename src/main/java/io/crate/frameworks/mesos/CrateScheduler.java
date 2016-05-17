/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.frameworks.mesos;

import com.google.protobuf.ByteString;
import io.crate.action.sql.SQLActionException;
import io.crate.action.sql.SQLRequest;
import io.crate.client.CrateClient;
import io.crate.frameworks.mesos.api.CrateHttpService;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.frameworks.mesos.config.Resources;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;
import org.apache.mesos.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.crate.frameworks.mesos.SaneProtos.taskID;
import static java.util.Arrays.asList;


public class CrateScheduler implements Scheduler {

    private static final String JAR_NAME = new File(CrateHttpService.jarLocation().getFile()).getName();
    private final ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(32);
    private final HashMap<String, RetryTask> retryTasks = new HashMap<>();
    private String hostIP;

    /**
     * Task that removes a slaveId from the list of excluded slaves
     * after a certain amount of time.
     */
    private class RetryTask implements Runnable {

        private static final long MAX_DELAY = 600_000L; // 10min
        private final String slaveId;
        private final String reason;

        private final AtomicInteger retryCount = new AtomicInteger(0);

        public RetryTask(String slaveId, String reason) {
            this.slaveId = slaveId;
            this.reason = reason;
        }

        private long calculateDelay(long count) {
            return Math.min(count * count * 1000L, MAX_DELAY);
        }

        public long delay() {
            return calculateDelay(retryCount.intValue());
        }

        public long incrementAndGetDelay() {
            return calculateDelay(retryCount.incrementAndGet());
        }

        @Override
        public void run() {
            LOGGER.debug("Remove {} from list of excluded slaves", slaveId);
            stateStore.state().removeSlaveIdFromExcludeList(reason, slaveId);
        }
    }

    private class InstancesObserver implements Observer<Integer> {
        private SchedulerDriver driver;

        public InstancesObserver(SchedulerDriver driver) {
            this.driver = driver;
        }

        public void update(Integer data) {
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
        String[] version = MesosNativeLibrary.VERSION.split("\\.", -1);
        int major = Integer.parseInt(version[0]);
        int minor = Integer.parseInt(version[1]);
        if (major == 0 && minor < 21) {
            // There is already a JIRA ticket for proper version validation.
            // todo: improve version validation once available
            LOGGER.error("Crate Framework requires MesosNativeLibrary >= 0.21.0, version is {}. Shutting down driver!",
                    MesosNativeLibrary.VERSION);
            driver.stop();
        }
        LOGGER.info("Registered framework with frameworkId {}", frameworkID.getValue());
        hostIP = Main.host();
        CrateState state = stateStore.state();
        assert stateStore.state() != null : "State must not be null";
        state.frameworkId(frameworkID.getValue());
        stateStore.save();
        crateInstances = state.crateInstances();

        instancesObserver.driver(driver);
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
        for (String reason : state.excludedSlaves().keySet()) {
            for (String slaveId : state.excludedSlaveIds(reason)) {
                scheduleReAddSlaveId(reason, slaveId);
            }

        }

        state.desiredInstances(configuration.nodeCount);
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Reregistered framework. Starting task reconciliation.");
        hostIP = Main.host();
        CrateState state = stateStore.state();
        assert stateStore.state() != null : "State must not be null";
        crateInstances = state.crateInstances();
        instancesObserver.driver(driver);
        state.desiredInstances().clearObservers();
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        if (!reconcileTasks.isEmpty()) {
            LOGGER.info("reconcileTasks size={}", reconcileTasks.size());
            declineAllOffers(driver, offers);
            LOGGER.info("declined offers = {}", offers);
            return;
        }
        CrateState state = stateStore.state();
        int required = state.missingInstances();
        if (required == 0) {
            declineAllOffers(driver, offers);
        } else if (required < 0) {
            killInstances(driver, required * -1);
            declineAllOffers(driver, offers);
        } else {
            int launched = 0;
            for (Protos.Offer offer : offers) {
                if (launched == required || !slaveWithRunningInstance(offer.getSlaveId().getValue())) {
                    driver.declineOffer(offer.getId());
                    continue;
                }
                CrateExecutableInfo crateInfo = obtainExecInfo(offer, offer.getAttributesList());
                if (crateInfo == null) {
                    driver.declineOffer(offer.getId());
                } else {
                    Protos.TaskID taskId = taskID(UUID.randomUUID().toString());
                    Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                            .setName(configuration.clusterName)
                            .setTaskId(taskId)
                            .setData(ByteString.copyFrom(crateInfo.toStream()))
                            .setExecutor(createExecutor())
                            .setSlaveId(offer.getSlaveId())
                            .addAllResources(configuration.getAllRequiredResources())
                            .build();

                    final String version = crateInstances.size() > 0 ? crateInstances.get(0).version() : configuration.version;
                    crateInstances.addInstance(new CrateInstance(
                            offer.getHostname(),
                            taskId.getValue(),
                            version,
                            configuration.transportPort,
                            taskInfo.getExecutor().getExecutorId().getValue(),
                            taskInfo.getSlaveId().getValue()
                    ));
                    state.instances(crateInstances);

                    Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
                    driver.launchTasks(asList(offer.getId()), asList(taskInfo), filters);
                    launched++;
                }
                stateStore.state().slavesWithInstances().remove(offer.getSlaveId().getValue());
            }
            stateStore.save();
        }

    }

    private boolean slaveWithRunningInstance(String slaveId) {
        return stateStore.state().slavesWithInstances().isEmpty() ||
                stateStore.state().slavesWithInstances().contains(slaveId);

    }

    @NotNull
    private Protos.ExecutorInfo createExecutor() {
        String path = String.format("http://%s:%d/static/%s", hostIP, configuration.apiPort, JAR_NAME);
        Protos.CommandInfo cmd = Protos.CommandInfo.newBuilder()
                .addAllUris(Arrays.asList(
                        Protos.CommandInfo.URI.newBuilder()
                                .setValue(path)
                                .setExtract(false)
                                .build(),
                        Protos.CommandInfo.URI.newBuilder()
                                .setValue(Main.JAVA_URL)
                                .setExtract(true)
                                .build()
                ))
                .setValue(
                        String.format("env && $(pwd)/jre/bin/java -cp %s io.crate.frameworks.mesos.CrateExecutor", JAR_NAME)
                )
                .build();
        return Protos.ExecutorInfo.newBuilder()
                .setName("Crate Executor")
                .setExecutorId(
                        Protos.ExecutorID.newBuilder()
                                .setValue(UUID.randomUUID().toString())
                                .build()
                )
                .setCommand(cmd)
                .build();
    }

    private CrateExecutableInfo obtainExecInfo(Protos.Offer offer, List<Protos.Attribute> attributes) {
        if (crateInstances.anyOnHost(offer.getHostname())) {
            LOGGER.debug("got already an instance on {}, rejecting offer {}", offer.getHostname(), offer.getId().getValue());
            return null;
        }
        if (!Resources.matches(offer.getResourcesList(), configuration)) {
            LOGGER.debug("can't use offer {}; not enough resources", offer.getId().getValue());
            return null;
        }
        if (offer.hasSlaveId() && stateStore.state().excludedSlaveIds().contains(offer.getSlaveId().getValue())) {
            LOGGER.debug("can't use offer {}; slaveId {} is blacklisted", offer.getId().getValue(), offer.getSlaveId().getValue());
            return null;
        }
        return new CrateExecutableInfo(
                configuration,
                offer.getHostname(),
                crateInstances,
                attributes,
                stateStore.state().desiredInstances().getValue()
        );
    }

    private void declineAllOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        for (Protos.Offer offer : offers) {
            driver.declineOffer(offer.getId());
        }
    }

    private void killInstances(SchedulerDriver driver, int toKill) {
        if (toKill == 0) return;
        if (toKill == crateInstances.size()) {
            markForceShutdown(driver);
        }
        int killed = 0;
        // TODO: need to check cluster state to make sure cluster has enough time to re-balance between kills
        LOGGER.debug("Too many instances running. Killing {} tasks", toKill);
        for (CrateInstance crateInstance : crateInstances) {
            if (killed == toKill) {
                break;
            }
            LOGGER.info("Kill task {}", crateInstance.taskId());
            driver.killTask(taskID(crateInstance.taskId()));
            killed++;
        }
    }

    private void markForceShutdown(SchedulerDriver driver) {
        CrateMessage clusterShutdownMessage = new CrateMessage<>(CrateMessage.Type.MESSAGE_CLUSTER_SHUTDOWN, "FORCE_SHUTDOWN");
        for (CrateInstance crateInstance : crateInstances) {
            LOGGER.debug("Force shutdown on executorID: {}, slaveID: {}", crateInstance.executorID(), crateInstance.slaveID());
            stateStore.state().slavesWithInstances().add(crateInstance.slaveID());
            driver.sendFrameworkMessage(Protos.ExecutorID.newBuilder().setValue(crateInstance.executorID()).build(),
                    Protos.SlaveID.newBuilder().setValue(crateInstance.slaveID()).build(), clusterShutdownMessage.toStream());
        }
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerID) {
        LOGGER.info("Offer rescinded: {}", offerID);
        // if any pending on that offer remove them?
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus taskStatus) {
        final String taskId = taskStatus.getTaskId().getValue();
        LOGGER.info("statusUpdate() {}", taskStatus.getMessage());
        LOGGER.debug("{} {}", taskStatus.getState(), taskId);

        if (!reconcileTasks.isEmpty()) {
            for (int i = reconcileTasks.size()-1; i >= 0; i--) {
                if (reconcileTasks.get(i).getTaskId().getValue().equals(taskId)) {
                    LOGGER.debug("remove reconcile task: {}", i, reconcileTasks.get(i));
                    reconcileTasks.remove(i);

                    if (taskStatus.getState() != Protos.TaskState.TASK_LOST) {
                        CrateInstance instance = crateInstances.byTaskId(taskId);
                        if (instance == null) {
                            LOGGER.error("Got a task for an instance that isn't tracked. HELP :(");
                        } else if (!instance.version().equals(configuration.version)) {
                            LOGGER.warn("Running instance has version {}, Configured is {}. " +
                                    "If you are not upgrading your cluster, make sure you configured your framework correctly!", instance.version(), configuration.version);
                        }
                    }
                }
            }
            driver.reviveOffers();
        }

        switch (taskStatus.getState()) {
            case TASK_RUNNING:
                LOGGER.debug("update state to running ...");
                crateInstances.setToRunning(taskId, taskStatus.getData().toStringUtf8());
                retryTasks.remove(taskStatus.getSlaveId().getValue());
                stateStore.state().removeSlaveIdFromExcludeList(taskStatus.getSlaveId().getValue());
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                // Crate node is about to start.
                LOGGER.debug("Waiting for new node to start ...");
                break;
            case TASK_KILLING:
                // Crate node is about to be killed.
                LOGGER.debug("Waiting for node to stop ...");
                break;
            case TASK_LOST:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_FINISHED:
            case TASK_ERROR:
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
        int instancesMissing = stateStore.state().missingInstances();
        if (instancesMissing != 0) {
            LOGGER.debug("Resize cluster. {} missing instances.", instancesMissing);
            if (instancesMissing > 0) {
                requestMoreResources(driver, instancesMissing);
            } else if (instancesMissing < 0) {
                killInstances(driver, instancesMissing * -1);
            }
        } else {
            LOGGER.trace("No missing instances, nothing to do.");
        }
        stateStore.save();
    }

    private void requestMoreResources(SchedulerDriver driver, int instancesMissing) {
        LOGGER.debug("asking for more resources for {} more instances", instancesMissing);
        List<Protos.Request> requests = new ArrayList<>(instancesMissing);
        for (int i = 0; i < instancesMissing; i++) {
            requests.add(
                    Protos.Request.newBuilder()
                            .addAllResources(configuration.getAllRequiredResources())
                            .build()
            );
        }
        driver.requestResources(requests);
    }

    @Override
    public void frameworkMessage(SchedulerDriver driver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, byte[] bytes) {
        LOGGER.info("Received framework message from executor {} on slave {}", executorID.getValue(), slaveID.getValue());
        CrateMessage data;
        try {
            data = CrateMessage.fromStream(bytes);
        } catch (IOException e) {
            LOGGER.error("Failed to read message from stream.", e);
            return;
        }
        switch (data.type()) {
            case MESSAGE_MISSING_RESOURCE:
                MessageMissingResource.Reason reason = ((MessageMissingResource) data.data()).reason();
                LOGGER.info("Remove bad host from offers: {} Reason: {}", slaveID.getValue(), reason.toString());
                stateStore.state().addSlaveIdToExcludeList(reason.toString(), slaveID.getValue());
                stateStore.save();
                scheduleReAddSlaveId(reason.toString(), slaveID.getValue());
                break;
            default:
                LOGGER.info("Switched on none cased data type: {}", data.type());
        }
    }

    /**
     * Remove slaveId from list of excluded slaves after delay.
     */
    private void scheduleReAddSlaveId(final String reason, final String slaveID) {
        RetryTask task = null;
        if (retryTasks.containsKey(slaveID)) {
            task = retryTasks.get(slaveID);
        }
        if (task == null) {
            task = new RetryTask(slaveID, reason);
            retryTasks.put(slaveID, task);
        }
        threadPoolExecutor.schedule(task, task.incrementAndGetDelay(), TimeUnit.MILLISECONDS);
        LOGGER.debug("Waiting for {}ms to use slave {} again ...", task.delay(), slaveID);
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        LOGGER.info("disconnected(driver : {})", driver);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID slaveID) {
        LOGGER.info("Slave lost slaveId=" + slaveID.getValue());
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorID, Protos.SlaveID slaveID, int i) {
        LOGGER.info("Executor lost: executorId=" + executorID.getValue()
                + " slaveId=" + slaveID.getValue() + " status=" + i);
    }

    @Override
    public void error(SchedulerDriver driver, String s) {
        LOGGER.error("error() {}", s);
        driver.stop();
    }

    void reconcileTasks(SchedulerDriver driver) {
        LOGGER.debug("Reconciling tasks ... {}", crateInstances.size());
        if (crateInstances.size() > 0) {
            reconcileTasks = new ArrayList<>(crateInstances.size());
            for (CrateInstance instance : crateInstances) {
                Protos.TaskState state = instance.state() == CrateInstance.State.RUNNING
                        ? Protos.TaskState.TASK_RUNNING
                        : Protos.TaskState.TASK_STARTING;
                Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder();
                builder.setState(state);
                builder.setTaskId(taskID(instance.taskId()));
                reconcileTasks.add(builder.build());
            }
            driver.reconcileTasks(reconcileTasks);
        }
    }

}

