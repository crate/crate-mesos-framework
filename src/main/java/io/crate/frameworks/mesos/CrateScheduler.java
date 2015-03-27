package io.crate.frameworks.mesos;

import com.google.protobuf.ByteString;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.frameworks.mesos.config.Resources;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.crate.frameworks.mesos.SaneProtos.taskID;


public class CrateScheduler implements Scheduler {

    private String hostIP;

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
        hostIP = hostIp(masterInfo);
        CrateState state = stateStore.state();

        state.frameworkId(frameworkID.getValue());
        stateStore.save();
        crateInstances = state.crateInstances();

        instancesObserver.driver(driver);
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Reregistered framwork. Starting task reconciliation.");
        hostIP = hostIp(masterInfo);
        CrateState state = stateStore.state();
        crateInstances = state.crateInstances();
        instancesObserver.driver(driver);
        state.desiredInstances().clearObservers();
        state.desiredInstances().addObserver(instancesObserver);
        reconcileTasks(driver);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        if (!reconcileTasks.isEmpty()) {
            LOGGER.info("Declining all offers ... got some reconcile tasks");
            declineAllOffers(driver, offers);
            return;
        }

        CrateState state = stateStore.state();
        int required = state.missingInstances();
        if (required == 0) {
            // nothing to do ...
            declineAllOffers(driver, offers);
        } else if (required < 0) {
            // kill redundant instances ...
            killInstances(driver, required * -1);
            declineAllOffers(driver, offers);
        } else {
            LOGGER.debug("Missing instances: {}", required);

            int launched = 0;
            for (Protos.Offer offer : offers) {
                if (launched == required) {
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

                    crateInstances.addInstance(new CrateInstance(
                            offer.getHostname(),
                            taskId.getValue(),
                            configuration.version,
                            configuration.transportPort
                    ));
                    state.instances(crateInstances);

                    Protos.Filters filters = Protos.Filters.newBuilder().setRefuseSeconds(1).build();
                    driver.launchTasks(Arrays.asList(offer.getId()), Arrays.asList(taskInfo), filters);
                    LOGGER.info("Submitted task ... {}", taskInfo.getTaskId().getValue());
                    launched++;
                }
            }
            stateStore.save();
        }

    }

    @NotNull
    private Protos.ExecutorInfo createExecutor() {
        final String jar = "crate-mesos.jar";
        String path = String.format("http://%s:%d/static/%s", hostIP, configuration.apiPort, jar);
        Protos.CommandInfo cmd = Protos.CommandInfo.newBuilder()
                .addUris(Protos.CommandInfo.URI.newBuilder().setValue(path).setExtract(false).build())
                .setValue(String.format("java -cp %s io.crate.frameworks.mesos.CrateExecutor", jar))
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
            LOGGER.info("got already an instance on {}, rejecting offer {}", offer.getHostname(), offer.getId().getValue());
            return null;
        }
        if (!Resources.matches(offer.getResourcesList(), configuration)) {
            LOGGER.info("can't use offer {}; not enough resources", offer.getId().getValue());
            return null;
        }
        return new CrateExecutableInfo(
                configuration,
                offer.getHostname(),
                crateInstances,
                attributes
        );
    }

    private void declineAllOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        for (Protos.Offer offer : offers) {
            driver.declineOffer(offer.getId());
        }
    }

    private void killInstances(SchedulerDriver driver, int toKill) {
        if (toKill == 0) return;
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
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerID) {
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

                    if (taskStatus.getState() != Protos.TaskState.TASK_LOST) {
                        CrateInstance instance = crateInstances.byTaskId(taskId);
                        if (instance == null) {
                            LOGGER.error("Got a task for an instance that isn't tracked. HELP :(");
                        } else if (!instance.version().equals(configuration.version)) {
                            LOGGER.info("Running instance has version {}. Configured is {}. Will change configuration to {}",
                                    instance.version(), configuration.version, instance.version()
                            );
                            configuration.version(instance.version());
                        }
                    }
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
        if (instancesMissing == 0) return;
        LOGGER.debug("Resize cluster. {} missing instances.", instancesMissing);
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

    void reconcileTasks(SchedulerDriver driver) {
        LOGGER.debug("Reconciling tasks ... {}", crateInstances.size());
        if (crateInstances.size() > 0) {
            reconcileTasks = new ArrayList<>(crateInstances.size());
            for (CrateInstance instance : crateInstances) {
                Protos.TaskState state = instance.state() == CrateInstance.State.RUNNING
                        ? Protos.TaskState.TASK_RUNNING
                        : Protos.TaskState.TASK_STARTING;
                LOGGER.debug("taskID {} instance={}", instance.taskId(), instance);
                Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder();
                builder.setState(state);
                builder.setTaskId(taskID(instance.taskId()));
                reconcileTasks.add(builder.build());
            }
            driver.reconcileTasks(reconcileTasks);
        }
    }

    private static String hostIp(Protos.MasterInfo masterInfo) {
        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            ip = "127.0.0.1";
        }
        return ip;
    }

    public static String intToIp(int i) {
        return String.format("%d.%d.%d.%d",
                ((i >> 24 ) & 0xFF),
                ((i >> 16 ) & 0xFF),
                ((i >> 8 ) & 0xFF),
                ( i & 0xFF)
        );
    }
}

