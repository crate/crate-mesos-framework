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

import com.google.common.base.Joiner;
import com.google.protobuf.ByteString;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;


public class CrateExecutor implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutor.class);
    private Task task;
    private File workingDirectory;
    private TaskID currentTaskId = null;
    private ExecutorDriver driver;
    private ScheduledFuture<?> healthCheck;
    private Boolean forceShutdown = false;
    private final ScheduledExecutorService healthCheckScheduler = Executors.newScheduledThreadPool(1);

    private class StartupInspectionTask implements Runnable {

        private static final String STATEMENT = "SELECT id FROM sys.nodes WHERE name = ?";
        private final CrateClient client;
        private final Object[] args;

        public StartupInspectionTask(String host, String nodeName) {
            client = new CrateClient(host);
            args = new Object[] { nodeName };
        }

        @Override
        public void run() {
            SQLRequest request = new SQLRequest(STATEMENT, args);
            SQLResponse response = null;
            while (response == null) {
                try {
                    Thread.sleep(1000L);
                    response = client.sql(request).actionGet();
                } catch (InterruptedException e) {
                    LOGGER.error("Crate startup was interrupted. Could not obtain node id.", e);
                } catch (Exception e) {
                    LOGGER.debug("Crate node is not running yet ... waiting to start up!");
                    response = null;
                }
            }
            client.close();
            onCrateClientResponse(response);
        }

    }

    @Override
    public void registered(ExecutorDriver driver, ExecutorInfo executorInfo, FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
        LOGGER.info("Registered executor {}", executorInfo.getExecutorId().getValue());
        this.driver = driver;
    }

    @Override
    public void reregistered(ExecutorDriver driver, SlaveInfo slaveInfo) {
        LOGGER.info("Re-registered executor");
        this.driver = driver;
    }

    @Override
    public void disconnected(ExecutorDriver driver) {
        LOGGER.warn("CrateExecutor was disconnected from driver {}", driver);
    }

    @Override
    public void launchTask(ExecutorDriver driver, TaskInfo taskInfo) {
        if (task != null && taskInfo.getTaskId().equals(currentTaskId)) {
            LOGGER.warn("Task {} already running ... do nothing!", currentTaskId.getValue());
            return;
        }
        currentTaskId = taskInfo.getTaskId();
        sendTaskStatus(driver, TaskState.TASK_STARTING);

        CrateExecutableInfo crateTask = null;
        try {
            crateTask = CrateExecutableInfo.fromStream(taskInfo.getData().toByteArray());
        } catch (IOException e) {
            LOGGER.error("Could not de-serialize TaskInfo", e);
        }
        if (crateTask != null) {
            LOGGER.debug("Prepare crateTask: {}", crateTask);
            boolean prepared = prepare(driver, crateTask);
            if (prepared) {
                task = new Task(crateTask);
                startProcess(driver, task);
                Thread startupCheck = new Thread(new StartupInspectionTask(
                        String.format("localhost:%s", crateTask.transportPort()),
                        crateTask.nodeName())
                );
                startupCheck.start();
                return;
            }
        }
        fail(driver);
    }

    @Override
    public void killTask(ExecutorDriver driver, TaskID taskId) {
        LOGGER.info("Killing task : " + taskId.getValue());
        healthCheck.cancel(true);
        int pid = task.pid();
        if (forceShutdown){
            forceShutdownCrate(driver);
        } else if (pid >= 0 && task.process != null) {
            LOGGER.debug("Found task to kill: " + taskId.getValue());
            gracefulShutdownCrate(driver);
        } else {
            LOGGER.error("No running task found. Stopping executor.");
            driver.sendStatusUpdate(TaskStatus.newBuilder()
                    .setTaskId(taskId)
                    .setState(TaskState.TASK_LOST)
                    .build());
            driver.stop();
        }
    }

    private void sendTaskStatus(ExecutorDriver driver, TaskState state){
        driver.sendStatusUpdate(TaskStatus.newBuilder()
                .setTaskId(currentTaskId)
                .setState(state)
                .build());
    }

    private void gracefulShutdownCrate(ExecutorDriver driver) {
        sendTaskStatus(driver, TaskState.TASK_KILLING);
        boolean success = task.gracefulStop();
        if (success) {
            sendTaskStatus(driver, TaskState.TASK_KILLED);
            driver.stop();
        } else {
            // Crate could not be stopped gracefully: send task back to RUNNING state!
            sendTaskStatus(driver, TaskState.TASK_RUNNING);
        }
    }

    public void forceShutdownCrate(ExecutorDriver driver) {
        sendTaskStatus(driver, TaskState.TASK_KILLING);
        LOGGER.debug("Stop Crate process.");
        task.destroy();
        sendTaskStatus(driver, TaskState.TASK_KILLED);
        driver.stop();
    }


    private void restartCrate(ExecutorDriver driver) {
        LOGGER.debug("Restart Crate process.");
        task.destroy();
        startProcess(driver, task);
    }


    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {
        try {
            CrateMessage crateMessage = CrateMessage.fromStream(data);
            if (crateMessage != null && crateMessage.type().equals(CrateMessage.Type.MESSAGE_CLUSTER_SHUTDOWN)) {
              forceShutdown = true;
            }
        } catch (IOException e) {
            LOGGER.error("Could not process message", e);
        }
    }

    @Override
    public void shutdown(ExecutorDriver driver) {
        LOGGER.warn("Executor driver is shutting down ...");
        forceShutdownCrate(driver);
    }

    @Override
    public void error(ExecutorDriver driver, String message) {
        LOGGER.error("Fatal error has occured with the executor driver and/or executor. {}", message);
    }

    private boolean prepare(ExecutorDriver driver, CrateExecutableInfo info) {
        workingDirectory = getOrCreateDataDir();
        File dataPath = info.dataDir();
        if (dataPath != null && (!dataPath.exists() || !dataPath.isDirectory())) {
            LOGGER.warn("Option -Des.path.data is set to {} but does not exist or is not a directory.",
                    dataPath.getAbsolutePath());
            CrateMessage<MessageMissingResource> msg = new CrateMessage<>(CrateMessage.Type.MESSAGE_MISSING_RESOURCE,
                    MessageMissingResource.MISSING_DATA_PATH);
            driver.sendFrameworkMessage(msg.toStream());
            return false;
        }
        File blobPath = info.blobDir();
        if (blobPath != null && (!blobPath.exists() || !blobPath.isDirectory())) {
            LOGGER.warn("Option -Des.path.blobs is set to {} but does not exist or is not a directory.",
                    blobPath.getAbsolutePath());
            CrateMessage<MessageMissingResource> msg = new CrateMessage<>(CrateMessage.Type.MESSAGE_MISSING_RESOURCE,
                    MessageMissingResource.MISSING_BLOB_PATH);
            driver.sendFrameworkMessage(msg.toStream());
            return false;
        }
        boolean success = true;
        for (URI uri : info.uris()) {
            success = fetchAndExtractUri(uri);
            if (!success) {
                break;
            }
        }
        return success;
    }

    private boolean fetchAndExtractUri(URI uri) {
        boolean success;
        try {
            URL download = uri.toURL();
            String fn = new File(download.getFile()).getName();
            File tmpFile = new File(fn);
            if (!tmpFile.exists()) {
                if(tmpFile.createNewFile()) {
                  LOGGER.debug("Fetch: {} -> {}", download, tmpFile);
                  ReadableByteChannel rbc = Channels.newChannel(download.openStream());
                  FileOutputStream stream = new FileOutputStream(tmpFile);
                  stream.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                }
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
        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                LOGGER.error("Failed to create working directory {}", dataDir.getAbsolutePath());
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
                            "-xf", tmpFile.getAbsolutePath()
                    },
                    new String[]{},
                    workingDirectory
            );
            process.waitFor();
        } catch (IOException|InterruptedException e) {
            LOGGER.error("Failed to extract file", e);
            success = false;
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
                // TODO: do we really want to redirect the Crate log output to stdout?
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
        cancelHealthCheckIfExists();
        healthCheckScheduler.shutdown();
        sendTaskStatus(driver, TaskState.TASK_FAILED);
        driver.stop();
    }

    protected void redirectProcess(Process process) {
        StreamRedirect stdoutRedirect = new StreamRedirect(process.getInputStream(), System.out);
        stdoutRedirect.start();
        StreamRedirect stderrRedirect = new StreamRedirect(process.getErrorStream(), System.err);
        stderrRedirect.start();
    }

    private void onCrateClientResponse(SQLResponse response) {
        TaskStatus.Builder status = TaskStatus.newBuilder()
                .setTaskId(currentTaskId)
                .setState(TaskState.TASK_RUNNING);
        if (response != null) {
            String nodeId = (String) response.rows()[0][0];
            LOGGER.info("NODE ID = {}", nodeId);
            status.setData(ByteString.copyFromUtf8(nodeId));
        }
        driver.sendStatusUpdate(status.build());
        cancelHealthCheckIfExists();
        healthCheck = scheduleHealthCheck(driver, "localhost", task.executableInfo.httpPort());
    }

    private void cancelHealthCheckIfExists() {
        if (healthCheck != null && (!healthCheck.isCancelled() || !healthCheck.isDone())) {
            healthCheck.cancel(false);
        }
    }

    public ScheduledFuture<?> scheduleHealthCheck(final ExecutorDriver driver, final String host, final Integer port) {
        final Runnable checker = new Runnable() {
            public void run() {
                HttpClient client = HttpClientBuilder.create()
                        .setDefaultRequestConfig(
                                RequestConfig.custom()
                                        .setConnectTimeout(5000) // 5s timeout
                                        .build()
                        ).build();
                HttpGet request = new HttpGet("http://" + host + ":" + port);
                try {
                    HttpResponse response = client.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(!(statusCode >= 200 || statusCode < 300)) {
                        LOGGER.error("Health check failed: Crate returned status {}. Waiting ...", statusCode);
                    } else {
                        LOGGER.info("Health check: OK ({})", statusCode);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to perform health check:", e);
                    int exitCode = -1;
                    try {
                        LOGGER.debug("Check if process already exited ...");
                        exitCode = task.process.exitValue();
                    } catch (IllegalThreadStateException ex) {
                        LOGGER.warn("Health check failed, but process is still running. Kill it!");
                        task.process.destroy();
                        task.process = null;
                        fail(driver);
                    }
                    if (exitCode >= 0) {
                        LOGGER.error("Crate process exited with status {}. Restarting now ...", exitCode);
                        restartCrate(driver);
                    }
                }
            }
        };
        return healthCheckScheduler.scheduleWithFixedDelay(checker, 10, 10, SECONDS);
    }

    public class Task {

        private final CrateExecutableInfo executableInfo;
        public Process process = null;

        Task(CrateExecutableInfo info) {
            this.executableInfo = info;

        }

        @NotNull
        private String env() {
            List<Environment.Variable> env = executableInfo.environment();
            ArrayList<String> vars = new ArrayList<>(env.size());
            for (Environment.Variable variable : env) {
                vars.add(String.format("%s=%s", variable.getName(), variable.getValue()));
            }
            // The Crate executable is using the Java executable from within the JAVA_HOME folder.
            vars.add("JAVA_HOME=$(pwd)/jre");
            return Joiner.on(" ").join(vars);
        }

        @NotNull
        private String cmd() {
            return Joiner.on(" ").join(executableInfo.arguments());
        }

        public Process run() throws IOException {
            final String runCmd = String.format("%s %s", env(), cmd());
            LOGGER.info("Launch task: {}", runCmd);
            process = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", runCmd},
                    new String[]{},
                    workingDirectory
            );
            return process;
        }

        public int pid() {
            FileInputStream pidFile = null;
            BufferedReader in = null;
            try {
                pidFile = new FileInputStream("crate.pid");
                in = new BufferedReader(new InputStreamReader(pidFile, Charset.defaultCharset()));
                return Integer.parseInt(in.readLine());
            } catch (IOException e) {
                LOGGER.error("Reading PID from crate.pid failed.");
            } finally {
              IOUtils.closeQuietly(in);
              IOUtils.closeQuietly(pidFile);
            }
            return -1;
        }

        public void destroy() {
            this.process.destroy();
            this.process = null;
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
                LOGGER.debug("Sending -USR2 signal to PID {}", pid);
                Runtime.getRuntime().exec(new String[]{"kill", "-USR2", Integer.toString(pid)});
                // todo: set timeout correctly
                shutdown.join(7_200_000L); // 60 * 60 * 2 * 1000;
                LOGGER.debug("Crate process exited with code {}", worker.exitCode);
                if (worker.exitCode == -1) {
                   throw new InterruptedIOException();
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Graceful shutdown task still running. We ran into a timeout :(", e);
                success = false;
            }
            return success;
        }
    }

    private static void addShutdownHook(final CrateExecutor executor, final MesosExecutorDriver driver) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            executor.forceShutdownCrate(driver);
          }
        });
    }

    /**
     * Main method for executor.
     */
    public static void main(String[] args) throws IOException {
        BasicConfigurator.configure();
        LOGGER.debug("Launch executor process ...");
        final CrateExecutor executor = new CrateExecutor();
        MesosExecutorDriver driver = new MesosExecutorDriver(executor);
        addShutdownHook(executor, driver);
        System.exit(driver.run() == Status.DRIVER_STOPPED ? 0 : 1);
    }
}
