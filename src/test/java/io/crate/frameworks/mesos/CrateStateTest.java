package io.crate.frameworks.mesos;

import com.google.common.base.Optional;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

public class CrateStateTest {

    @Test
    public void testSerializeCrateState() throws Throwable {
        CrateState state = new CrateState();
        state.desiredInstances(42);
        state.frameworkId("crate-mesos");
        byte[] bytes = state.toStream();

        CrateState stateDeserialized = CrateState.fromStream(bytes);
        assertEquals(42, (int) stateDeserialized.desiredInstances().getValue());
        assertTrue(stateDeserialized.frameworkId().isPresent());
        assertEquals(Optional.of("crate-mesos"), stateDeserialized.frameworkId());
        assertEquals(0, stateDeserialized.crateInstances().size());

    }

    @Test
    public void testSerializeCrateStateWithInstances() throws Throwable {
        CrateState state = new CrateState();
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0", 4300));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0", 4300));
        cluster.setToRunning("1", "id-1");
        state.instances(cluster);

        byte[] bytes = state.toStream();

        CrateState stateDeserialized = CrateState.fromStream(bytes);
        assertEquals(2, stateDeserialized.crateInstances().size());

        HashSet<CrateInstance.State> states = new HashSet<>(2);
        for (CrateInstance instance : stateDeserialized.crateInstances()) {
            states.add(instance.state());
        }
        assertEquals(new HashSet<CrateInstance.State>(){
            {
                add(CrateInstance.State.RUNNING);
                add(CrateInstance.State.PENDING);
            }
        }, states);
    }
}