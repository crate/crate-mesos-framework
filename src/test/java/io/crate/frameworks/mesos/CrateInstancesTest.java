package io.crate.frameworks.mesos;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;


public class CrateInstancesTest {

    @Test
    public void testSize() throws Exception {
        CrateInstances cluster = new CrateInstances();
        assertEquals(0, cluster.size());

        cluster.addInstance(new CrateInstance("localhost", "1", "0.47.0"));
        assertEquals(1, cluster.size());
    }

    @Test
    public void testAnyOnHost() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0"));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0"));
        assertTrue(cluster.anyOnHost("127.0.0.1"));
        assertFalse(cluster.anyOnHost("127.0.0.3"));
    }

    @Test
    public void testHosts() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0"));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0"));
        assertEquals(new HashSet<String>(){
            {
                add("127.0.0.1");
                add("127.0.0.2");
            }
        }, cluster.hosts());
    }

    @Test
    public void testSetToRunning() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0"));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0"));

        cluster.setToRunning("1");

        HashSet<CrateInstance.State> states = new HashSet<>(2);
        for (CrateInstance instance : cluster) {
            states.add(instance.state());
        }
        assertEquals(new HashSet<CrateInstance.State>(){
            {
                add(CrateInstance.State.RUNNING);
                add(CrateInstance.State.PENDING);
            }
        }, states);

    }

    @Test
    public void testRemoveTask() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0"));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0"));
        cluster.removeTask("1");
        assertEquals(1, cluster.size());
        assertFalse(cluster.anyOnHost("127.0.0.1"));
        assertTrue(cluster.anyOnHost("127.0.0.2"));
        cluster.removeTask("2");
        assertEquals(new HashSet<String>(), cluster.hosts());
    }
}