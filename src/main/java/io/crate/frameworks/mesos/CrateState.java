package io.crate.frameworks.mesos;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.state.Variable;
import org.apache.mesos.state.ZooKeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CrateState {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateState.class);

    private final ZooKeeperState zkState;
    private Variable lastState = null;

    public CrateState(ZooKeeperState zkState) {
        this.zkState = zkState;
    }

    public CrateInstances retrieveState() {
        LOGGER.info("retrieving state from zk");
        try {
            lastState = zkState.fetch("crate_instances").get();
            LOGGER.info("retrieved {} bytes from zk", lastState.value().length);
            CrateInstances crateInstances = CrateInstances.fromStream(lastState.value());
            LOGGER.info("received crateInstances from zk... got {} instances", crateInstances.size());

            return crateInstances;

        } catch (Exception e) {
            LOGGER.error("couldn't serialize CrateInstances", e);
            return new CrateInstances();
        }
    }

    public int desiredInstances() {
        // TODO: must be configurable
        return 1;
    }


    public void storeState(CrateInstances activeOrPendingInstances) {
        LOGGER.info("updating state with {} instances in zk", activeOrPendingInstances.size());
        zkState.store(lastState.mutate(activeOrPendingInstances.toStream()));
        LOGGER.info("stored new state");
    }
}
