package io.crate.frameworks.mesos;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import org.apache.mesos.state.Variable;
import org.apache.mesos.state.ZooKeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CrateState {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateState.class);

    private final ZooKeeperState zkState;

    private static final String INSTANCES = "crate_instances";
    private static final String FRAMEWORK_ID = "framework_id";

    private final Future<Variable> zkInstancesFuture;
    private final Future<Variable> zkFrameworkIDFuture;

    private Variable zkInstances = null;
    private Variable zkFrameworkID = null;

    public CrateState(ZooKeeperState zkState) {
        this.zkState = zkState;
        LOGGER.info("retrieving state from zk");
        zkInstancesFuture = zkState.fetch(INSTANCES);
        zkFrameworkIDFuture = zkState.fetch(FRAMEWORK_ID);
    }

    public CrateInstances crateInstances() {
        if (zkInstances == null) {
            try {
                zkInstances = zkInstancesFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error("Couldn't retrieve crateInstances from ZK");
                return new CrateInstances();
            }
        }
        try {
            CrateInstances crateInstances = CrateInstances.fromStream(zkInstances.value());
            LOGGER.info("received crateInstances from zk... got {} instances", crateInstances.size());
            return crateInstances;
        } catch (IOException e) {
            LOGGER.error("Couldn't read/serialize crateInstances");
            return new CrateInstances();
        }
    }

    public void instances(CrateInstances activeOrPendingInstances) {
        LOGGER.info("updating state with {} instances in zk", activeOrPendingInstances.size());
        zkState.store(zkInstances.mutate(activeOrPendingInstances.toStream()));
        LOGGER.info("stored new state");
    }

    public int desiredInstances() {
        // TODO: must be configurable
        return 1;
    }

    public void frameworkID(String frameworkId) {
        zkState.store(zkFrameworkID.mutate(frameworkId.getBytes(Charsets.UTF_8)));
        LOGGER.info("Stored frameworkID: {}", frameworkId);
    }

    public Optional<String> frameworkId() {
        if (zkFrameworkID == null) {
            try {
                zkFrameworkID = zkFrameworkIDFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error("error retrieving framework_id from zk", e);
                return Optional.absent();
            }
        }

        if (zkFrameworkID.value().length == 0) {
            LOGGER.info("ZK didn't have a frameworkID. Will get a new one");
            return Optional.absent();
        }


        String id = new String(zkFrameworkID.value(), Charsets.UTF_8);
        LOGGER.info("Re-using frameworkID {}", id);
        return Optional.of(id);
    }
}
