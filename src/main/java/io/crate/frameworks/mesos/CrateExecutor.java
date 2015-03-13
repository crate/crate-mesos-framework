package io.crate.frameworks.mesos;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CrateExecutor implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutor.class);

    @Override
    public void registered(ExecutorDriver driver, ExecutorInfo executorInfo, FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
        LOGGER.debug("registered() driver={} executorInfo={} frameworkInfo={} slaveInfo{}",
                driver, executorInfo, frameworkInfo, slaveInfo);
    }

    @Override
    public void reregistered(ExecutorDriver driver, SlaveInfo slaveInfo) {
        LOGGER.debug("reregistered() driver={} slaveInfo={}",
                driver, slaveInfo);
    }

    @Override
    public void disconnected(ExecutorDriver driver) {

    }

    @Override
    public void launchTask(ExecutorDriver driver, TaskInfo task) {
        LOGGER.debug("launchTask() driver={} task={}",
                driver, task);
    }

    @Override
    public void killTask(ExecutorDriver driver, TaskID taskId) {
        LOGGER.debug("launchTask() driver={} taskId={}",
                driver, taskId);
    }

    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {

    }

    @Override
    public void shutdown(ExecutorDriver driver) {

    }

    @Override
    public void error(ExecutorDriver driver, String message) {

    }
}
