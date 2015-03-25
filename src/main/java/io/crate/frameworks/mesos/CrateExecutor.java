package io.crate.frameworks.mesos;

import com.google.common.base.Joiner;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;


public class CrateExecutor implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutor.class);

    private Task task;
    private File dataDir;
    private TaskID currentTaskId;

    @Override
    public void registered(ExecutorDriver driver, ExecutorInfo executorInfo, FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
        LOGGER.debug("Registered executor {} for framework {}", executorInfo.getExecutorId().getValue(), frameworkInfo);
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
        TaskInfo crateTask = null;
        try {
            crateTask = TaskInfo.parseFrom(taskInfo.getData());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.debug("Could not de-serialize TaskInfo", e);
            e.printStackTrace();
        }
        if (crateTask != null) {
            LOGGER.debug("Prepare crateTask: {}", crateTask);
            boolean prepared = prepare(crateTask);
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
            task.process.destroy();
            task.process = null;
            driver.sendStatusUpdate(TaskStatus.newBuilder()
                    .setTaskId(taskId)
                    .setState(TaskState.TASK_KILLED)
                    .build());
            driver.stop();
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

    private boolean prepare(TaskInfo taskInfo) {
        dataDir = getOrCreateDataDir();
        boolean success = true;
        for (CommandInfo.URI uri : taskInfo.getCommand().getUrisList()) {
            success = fetchAndExtractUri(uri);
            if (!success) {
                break;
            }
        }
        return success;
    }

    private boolean fetchAndExtractUri(CommandInfo.URI uri) {
        boolean success;
        try {
            URL download = new URL(uri.getValue());
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
        LOGGER.debug("dataDir={}",dataDir);

        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                LOGGER.debug("Failed to create directory {}", dataDir.getAbsolutePath());
                System.exit(2);
            }
        }
        return dataDir;
    }

    private boolean extractFile(File tmpFile) {
        LOGGER.debug("Extracting file {} to {}", tmpFile.getName(), dataDir.getAbsolutePath());
        boolean success = true;
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{
                            "tar",
                            "-C", dataDir.getAbsolutePath(),
                            "-xf", tmpFile.getAbsolutePath(),
                            "--strip-components=1"
                    },
                    new String[]{},
                    dataDir
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
        System.exit(2);
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
        public TaskInfo taskInfo;
        public Process process = null;

        Task(TaskInfo taskInfo) {
            this.taskInfo = taskInfo;
            this.cmd = cmd();
            this.env = env();

        }

        private String env() {
            Environment environment = taskInfo.getCommand().getEnvironment();
            List<String> vars = new ArrayList<>(environment.getVariablesCount());
            for (Environment.Variable variable : environment.getVariablesList()) {
                vars.add(String.format("%s=%s", variable.getName(), variable.getValue()));
            }
            return Joiner.on(" ").join(vars);
        }

        private String cmd() {
            return Joiner.on(" ").join(taskInfo.getCommand().getArgumentsList());
        }

        public Process run() throws IOException {
            final String runCmd = String.format("%s %s", task.env, task.cmd);
            LOGGER.debug("Launch task: {}", runCmd);
            process = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", runCmd},
                    new String[]{},
                    dataDir
            );
            return process;
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
