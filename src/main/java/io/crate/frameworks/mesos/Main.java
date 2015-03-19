package io.crate.frameworks.mesos;

import com.google.common.base.Optional;
import com.google.protobuf.ByteString;
import io.crate.frameworks.mesos.api.CrateHttpService;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Scheduler;
import org.apache.mesos.state.ZooKeeperState;

import java.util.concurrent.TimeUnit;


public class Main {

    /**
     * Show command-line usage.
     */
    private static void usage() {
        String name = Main.class.getName();
        System.err.println("Usage: " + name + " master-ip-and-port number-of-instances");
    }

    /**
     * Command-line entry point.
     * <br/>
     * Example usage: java ExampleFramework 127.0.0.1:5050 1
     */
    public static void main(String[] args) throws Exception {
        // check command-line args
        if (args.length != 2) {
            usage();
            System.exit(1);
        }
        BasicConfigurator.configure();

        final int frameworkFailoverTimeout = 60 * 60;

        Protos.FrameworkInfo.Builder frameworkBuilder = Protos.FrameworkInfo.newBuilder()
                .setName("CrateFramework")
                .setUser("root")
                .setFailoverTimeout(frameworkFailoverTimeout); // timeout in seconds


        CrateState crateState = new CrateState(
                new ZooKeeperState("localhost:2181", 20_000, TimeUnit.MILLISECONDS, "/crate-mesos/CrateFramework"));

        Optional<String> frameworkId = crateState.frameworkId();
        if (frameworkId.isPresent()) {
            frameworkBuilder.setId(Protos.FrameworkID.newBuilder().setValue(frameworkId.get()).build());
        }

        if (System.getenv("MESOS_CHECKPOINT") != null) {
            System.out.println("Enabling checkpoint for the framework");
            frameworkBuilder.setCheckpoint(true);
        }

        // parse command-line args
        final String scriptName = args[0];

        ResourceConfiguration resourceConfiguration = ResourceConfiguration.fromEnvironment();
        final Scheduler scheduler = new CrateScheduler(crateState, resourceConfiguration);

        // create the driver
        MesosSchedulerDriver driver;
        if (System.getenv("MESOS_AUTHENTICATE") != null) {
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
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), scriptName, credential);
        } else {
            frameworkBuilder.setPrincipal("crate-framework");
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), scriptName);
        }

        CrateHttpService api = new CrateHttpService(crateState);
        api.start();

        int status = driver.run() == Status.DRIVER_STOPPED ? 0 : 1;

        // Ensure that the driver process terminates.
        api.stop();
        driver.stop();
        System.exit(status);
    }

}
