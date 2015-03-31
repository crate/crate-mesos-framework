package io.crate.frameworks.mesos;

import com.google.common.base.Joiner;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;


public class CrateExecutor implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutor.class);

    private Task task;
    private File workingDirectory;
    private TaskID currentTaskId;

    @Override
    public void registered(ExecutorDriver driver, ExecutorInfo executorInfo, FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
        LOGGER.debug("Registered executor {}", executorInfo.getExecutorId().getValue());
    }

    @Override
    public void reregistered(ExecutorDriver driver, SlaveInfo slaveInfo) {
        LOGGER.debug("Re-registered executor");
    }

    @Override
    public void disconnected(ExecutorDriver driver) {
        // todo: handle correctly
    }

    @Override
    public void launchTask(ExecutorDriver driver, TaskInfo taskInfo) {
        currentTaskId = taskInfo.getTaskId();
        driver.sendStatusUpdate(TaskStatus.newBuilder()
                .setTaskId(currentTaskId)
                .setState(TaskState.TASK_STARTING)
                .build());

        CrateExecutableInfo crateTask = null;
        try {
            crateTask = CrateExecutableInfo.fromStream(taskInfo.getData().toByteArray());
        } catch (IOException e) {
            LOGGER.debug("Could not de-serialize TaskInfo", e);
            e.printStackTrace();
        }
        if (crateTask != null) {
            LOGGER.debug("Prepare crateTask: {}", crateTask);
            boolean prepared = prepare(driver, crateTask);
            if (prepared) {
                task = new Task(crateTask);
                startProcess(driver, task);
                driver.sendStatusUpdate(TaskStatus.newBuilder()
                        .setTaskId(currentTaskId)
                        .setState(TaskState.TASK_RUNNING)
                        .build());
                return;
            }
        }
        fail(driver);
    }

    @Override
    public void killTask(ExecutorDriver driver, TaskID taskId) {
        LOGGER.info("Killing task : " + taskId.getValue());
        if (task.process != null) {
            LOGGER.info("Found task to kill: " + taskId.getValue());
            int pid = task.pid();
            if (pid == -1) {
                LOGGER.debug("Graceful shutdown failed. Could not read crate.pid.");
                task.process.destroy();
                task.process = null;
                return;
            }
            boolean success = task.gracefulStop();
            if (success) {
                driver.sendStatusUpdate(TaskStatus.newBuilder()
                        .setTaskId(taskId)
                        .setState(TaskState.TASK_KILLED)
                        .build());
                driver.stop();
            } else {
                driver.sendStatusUpdate(TaskStatus.newBuilder()
                        .setTaskId(taskId)
                        .setState(TaskState.TASK_RUNNING)
                        .build());
            }
        } else  {
            LOGGER.debug("No running task found :(");
        }
    }


    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {
    }

    @Override
    public void shutdown(ExecutorDriver driver) {
        LOGGER.debug("shutdown: {}", driver);
    }

    @Override
    public void error(ExecutorDriver driver, String message) {
        LOGGER.debug("error: {}", message);
    }

    private boolean prepare(ExecutorDriver driver, CrateExecutableInfo info) {
        workingDirectory = getOrCreateDataDir();
        File dataPath = info.dataDir();
        if (dataPath != null && (!dataPath.exists() || !dataPath.isDirectory())) {
            LOGGER.debug("Option -Des.path.data is set to {} but does not exist or is not a directory.",
                    dataPath.getAbsolutePath());
            CrateMessage<MessageMissingResource> msg = new CrateMessage<>(CrateMessage.Type.MESSAGE_MISSING_RESOURCE,
                    MessageMissingResource.MISSING_DATA_PATH);
            driver.sendFrameworkMessage(msg.toStream());
            LOGGER.debug("message = {}", msg.toStream());
            return false;
        }
        File blobPath = info.blobDir();
        if (blobPath != null && (!blobPath.exists() || !blobPath.isDirectory())) {
            LOGGER.debug("Option -Des.path.blobs is set to {} but does not exist or is not a directory.",
                    blobPath.getAbsolutePath());
            CrateMessage<MessageMissingResource> msg = new CrateMessage<>(CrateMessage.Type.MESSAGE_MISSING_RESOURCE,
                    MessageMissingResource.MISSING_BLOB_PATH);
            driver.sendFrameworkMessage(msg.toStream());
            LOGGER.debug("message = {}", msg.toStream());
            return false;
        }
        return fetchAndExtractUri(info.uri());
    }

    private boolean fetchAndExtractUri(URI uri) {
        boolean success;
        try {
            URL download = uri.toURL();
            String fn = new File(download.getFile()).getName();
            File tmpFile = new File(fn);
            if (!tmpFile.exists()) {
                tmpFile.createNewFile();
                LOGGER.debug("Fetch: {} -> {}", download, tmpFile);
                ReadableByteChannel rbc = Channels.newChannel(download.openStream());
                FileOutputStream stream = new FileOutputStream(tmpFile);
                stream.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } else {
                LOGGER.debug("tarball already downloaded");
            }
            success = extractFile(tmpFile);
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        return success;
    }

    @NotNull
    private File getOrCreateDataDir() {
        File dataDir = new File("crate.tmp").getAbsoluteFile().getParentFile();
        LOGGER.debug("workingDirectory={}",dataDir);

        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                LOGGER.debug("Failed to create directory {}", dataDir.getAbsolutePath());
                System.exit(2);
            }
        }
        return dataDir;
    }

    private boolean extractFile(File tmpFile) {
        LOGGER.debug("Extracting file {} to {}", tmpFile.getName(), workingDirectory.getAbsolutePath());
        boolean success = true;
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{
                            "tar",
                            "-C", workingDirectory.getAbsolutePath(),
                            "-xf", tmpFile.getAbsolutePath(),
                            "--strip-components=1"
                    },
                    new String[]{},
                    workingDirectory
            );
            process.waitFor();
        } catch (IOException|InterruptedException e) {
            LOGGER.error("Failed to extract file", e);
        }
        return success;
    }

    /**
     * Starts a task's process so it goes into running state.
     **/
    protected void startProcess(ExecutorDriver driver, Task task) {
        if (task.process == null) {
            try {
                task.run();
                redirectProcess(task.process);
                try {
                    Thread.sleep(10000);
                    task.process.exitValue();
                    fail(driver);
                } catch (InterruptedException | IllegalThreadStateException e) {
                    // task is still running, all good!
                    LOGGER.debug("task still running after 10s");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to run command", e);
                fail(driver);
            }
        } else {
            LOGGER.error("Tried to start process, but process already running");
        }
    }

    private void fail(ExecutorDriver driver) {
        driver.sendStatusUpdate(TaskStatus.newBuilder()
                .setTaskId(currentTaskId)
                .setState(TaskState.TASK_FAILED)
                .build());
        driver.stop();
    }

    protected void redirectProcess(Process process) {
        StreamRedirect stdoutRedirect = new StreamRedirect(process.getInputStream(), System.out);
        stdoutRedirect.start();
        StreamRedirect stderrRedirect = new StreamRedirect(process.getErrorStream(), System.err);
        stderrRedirect.start();
    }

    public class Task {

        private final String env;
        private final String cmd;
        public CrateExecutableInfo executableInfo;
        public Process process = null;

        Task(CrateExecutableInfo info) {
            this.executableInfo = info;
            this.cmd = cmd();
            this.env = env();

        }

        private String env() {
            List<Environment.Variable> env = executableInfo.environment();
            ArrayList<String> vars = new ArrayList<>(env.size());
            for (Environment.Variable variable : env) {
                vars.add(String.format("%s=%s", variable.getName(), variable.getValue()));
            }
            return Joiner.on(" ").join(vars);
        }

        private String cmd() {
            return Joiner.on(" ").join(executableInfo.arguments());
        }

        public Process run() throws IOException {
            final String runCmd = String.format("%s %s", task.env, task.cmd);
            LOGGER.debug("Launch task: {}", runCmd);
            process = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", runCmd},
                    new String[]{},
                    workingDirectory
            );
            return process;
        }

        public int pid() {
            try {
                FileInputStream pidFile = new FileInputStream("crate.pid");
                BufferedReader in = new BufferedReader(new InputStreamReader(pidFile));
                return Integer.valueOf(in.readLine());
            } catch (IOException e) {
                LOGGER.error("Reading PID from crate.pid failed.");
            }
            return -1;
        }

        class GracefulShutdownWorker implements Runnable {
            private final Process process;
            public int exitCode = -1;
            public GracefulShutdownWorker(Process process) {
                this.process = process;
            }
            @Override
            public void run() {
                try {
                    exitCode = this.process.waitFor();
                } catch (InterruptedException e) {
                    exitCode = -1;
                }
            }
        }

        public boolean gracefulStop() {
            int pid = pid();
            boolean success = true;
            try {
                GracefulShutdownWorker worker = new GracefulShutdownWorker(process);
                Thread shutdown = new Thread(worker);
                shutdown.start();
                LOGGER.debug("Sending -USR2 signale to PID {}", pid);
                Runtime.getRuntime().exec(new String[]{"kill", "-USR2", Integer.toString(pid)});
                // todo: set timeout correctly
                shutdown.join(7_200_000L); // 60 * 60 * 2 * 1000;
                LOGGER.debug("Crate process exited with code {}", worker.exitCode);
                if (worker.exitCode == -1) {
                   throw new InterruptedIOException();
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.debug("Graceful shutdown task still running. We ran into a timeout :(", e);
                success = false;
            }
            return success;
        }
    }

    /**
     * Main method for executor.
     */
    public static void main(String[] args) throws IOException {
        BasicConfigurator.configure();
        LOGGER.debug("Launch executor process ...");
        MesosExecutorDriver driver = new MesosExecutorDriver(new CrateExecutor());
        System.exit(driver.run() == Status.DRIVER_STOPPED ? 0 : 1);
    }
}
