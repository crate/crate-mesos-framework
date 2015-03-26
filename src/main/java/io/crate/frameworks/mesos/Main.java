package io.crate.frameworks.mesos;

import com.beust.jcommander.JCommander;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import io.crate.frameworks.mesos.api.CrateHttpService;
import io.crate.frameworks.mesos.config.Configuration;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.state.ZooKeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final Set<String> HELP_OPTIONS = Sets.newHashSet("-h", "--help", "help");
    private static final Set<String> PROTECTED_CRATE_ARGS = Sets.newHashSet(
            "-Des.cluster.name",
            "-Des.http.port",
            "-Des.transport.tcp.port",
            "-Des.node.name",
            "-Des.discovery.zen.ping.multicast.enabled",
            "-Des.discovery.zen.ping.unicast.hosts",
            "-Des.path.data",
            "-Des.path.blobs",
            "-Des.path.logs"
    );

    static Configuration parseConfiguration(String[] args) {
        Configuration configuration = new Configuration();
        JCommander jCommander;

        List<String> crateArgs = new ArrayList<>();
        List<String> safeArgs = new ArrayList<>(args.length);
        for (String arg : args) {
            if (HELP_OPTIONS.contains(arg)) {
                jCommander = new JCommander(configuration);
                jCommander.usage();
                System.exit(1);
            }
            if (arg.startsWith("-Des.")) {
                String argKey = arg.split("\\=")[0];
                if (PROTECTED_CRATE_ARGS.contains(argKey)) {
                    throw new IllegalArgumentException(
                            String.format("Argument \"%s\" is protected and managed by the framework. " +
                                    "It cannot be set by the user", argKey));
                } else {
                    crateArgs.add(arg);
                }
            } else {
                safeArgs.add(arg);
            }
        }
        jCommander = new JCommander(configuration, safeArgs.toArray(new String[safeArgs.size()]));
        configuration.crateArgs(crateArgs);
        LOGGER.debug("args: {}", configuration);
        return configuration;
    }


    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        Configuration configuration = parseConfiguration(args);

        final double frameworkFailoverTimeout = 60 * 60;

        Protos.FrameworkInfo.Builder frameworkBuilder = Protos.FrameworkInfo.newBuilder()
                .setName("CrateFramework")
                .setUser("")
                .setFailoverTimeout(frameworkFailoverTimeout); // timeout in seconds

        PersistentStateStore stateStore = new PersistentStateStore(
                new ZooKeeperState(configuration.zookeeper, 20_000, TimeUnit.MILLISECONDS,
                        String.format("/crate-mesos/%s", configuration.clusterName)),
                configuration.nodeCount);

        Optional<String> frameworkId = stateStore.state().frameworkId();
        if (frameworkId.isPresent()) {
            frameworkBuilder.setId(Protos.FrameworkID.newBuilder().setValue(frameworkId.get()).build());
        }

        if (System.getenv("MESOS_CHECKPOINT") != null) {
            System.out.println("Enabling checkpoint for the framework");
            frameworkBuilder.setCheckpoint(true);
        }

        final Scheduler scheduler = new CrateScheduler(stateStore, configuration);

        // create the driver
        MesosSchedulerDriver driver;

        String mesosMaster = configuration.mesosMaster();
        if (System.getenv("MESOS_AUTHENTICATE") != null) {
            // todo: authentication
            System.out.println("Enabling authentication for the framework");

            if (System.getenv("DEFAULT_PRINCIPAL") == null) {
                System.err.println("Expecting authentication principal in the environment");
                System.exit(1);
            }

            if (System.getenv("DEFAULT_SECRET") == null) {
                System.err.println("Expecting authentication secret in the environment");
                System.exit(1);
            }

            Protos.Credential credential = Protos.Credential.newBuilder()
                    .setPrincipal(System.getenv("DEFAULT_PRINCIPAL"))
                    .setSecret(ByteString.copyFrom(System.getenv("DEFAULT_SECRET").getBytes()))
                    .build();

            frameworkBuilder.setPrincipal(System.getenv("DEFAULT_PRINCIPAL"));
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster, credential);
        } else {
            frameworkBuilder.setPrincipal("crate-framework");
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster);
        }

        CrateHttpService api = new CrateHttpService(stateStore, configuration);
        api.start();
        int status = driver.run() == Protos.Status.DRIVER_STOPPED ? 0 : 1;

        // Ensure that the driver process terminates.
        api.stop();
        driver.stop();
        System.exit(status);
    }

}
