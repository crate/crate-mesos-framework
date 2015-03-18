package io.crate.frameworks.mesos;

import com.google.protobuf.ByteString;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Scheduler;

/**
 * Source code adapted from the example that ships with Mesos.
 */
public class Main {

    /** Show command-line usage. */
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

        // If the framework stops running, mesos will terminate all of the tasks that
        // were initiated by the framework but only once the fail-over timeout period
        // has expired. Using a timeout of zero here means that the tasks will
        // terminate immediately when the framework is terminated. For production
        // deployments this probably isn't the desired behavior, so a timeout can be
        // specified here, allowing another instance of the framework to take over.
        final int frameworkFailoverTimeout = 60 * 60;

        // TODO: need to persist frameworkID
        FrameworkInfo.Builder frameworkBuilder = FrameworkInfo.newBuilder()
                .setName("CrateFramework")
                .setUser("root")
                .setFailoverTimeout(frameworkFailoverTimeout); // timeout in seconds

        if (System.getenv("MESOS_CHECKPOINT") != null) {
            System.out.println("Enabling checkpoint for the framework");
            frameworkBuilder.setCheckpoint(true);
        }

        // parse command-line args
        final String scriptName = args[0];
        final int totalTasks = Integer.parseInt(args[1]);

        BasicConfigurator.configure();

        final Scheduler scheduler = new CrateScheduler(totalTasks);

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

            Credential credential = Credential.newBuilder()
                    .setPrincipal(System.getenv("DEFAULT_PRINCIPAL"))
                    .setSecret(ByteString.copyFrom(System.getenv("DEFAULT_SECRET").getBytes()))
                    .build();

            frameworkBuilder.setPrincipal(System.getenv("DEFAULT_PRINCIPAL"));

            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), scriptName, credential);
        } else {
            frameworkBuilder.setPrincipal("crate-framework");

            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), scriptName);
        }

        int status = driver.run() == Status.DRIVER_STOPPED ? 0 : 1;

        // Ensure that the driver process terminates.
        driver.stop();
        System.exit(status);
    }
}

