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

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;


public class CrateInstancesTest {

    private static CrateInstance newInstance(String hostname, String taskId) {
        return new CrateInstance(hostname, taskId, "0.47.0", 4300, "exec-1", "slave-1");
    }

    @Test
    public void testSize() throws Exception {
        CrateInstances cluster = new CrateInstances();
        assertEquals(0, cluster.size());

        cluster.addInstance(newInstance("localhost", "1"));
        assertEquals(1, cluster.size());
    }

    @Test
    public void testAnyOnHost() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(newInstance("127.0.0.1", "1"));
        cluster.addInstance(newInstance("127.0.0.2", "2"));
        assertTrue(cluster.anyOnHost("127.0.0.1"));
        assertFalse(cluster.anyOnHost("127.0.0.3"));
    }

    @Test
    public void testHosts() throws Exception {
        CrateInstances cluster = new CrateInstances();
        cluster.addInstance(newInstance("127.0.0.1", "1"));
        cluster.addInstance(newInstance("127.0.0.2", "2"));
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
        cluster.addInstance(newInstance("127.0.0.1", "1"));
        cluster.addInstance(newInstance("127.0.0.2", "2"));

        cluster.setToRunning("1", "id-1");

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
        cluster.addInstance(newInstance("127.0.0.1", "1"));
        cluster.addInstance(newInstance("127.0.0.2", "2"));
        cluster.removeTask("1");
        assertEquals(1, cluster.size());
        assertFalse(cluster.anyOnHost("127.0.0.1"));
        assertTrue(cluster.anyOnHost("127.0.0.2"));
        cluster.removeTask("2");
        assertEquals(new HashSet<String>(), cluster.hosts());
    }

    @Test
    public void testCalculateQuorum() throws Exception {
        assertEquals(1, CrateInstances.calculateQuorum(1));
        assertEquals(2, CrateInstances.calculateQuorum(2));
        assertEquals(2, CrateInstances.calculateQuorum(3));
        assertEquals(3, CrateInstances.calculateQuorum(4));
        assertEquals(3, CrateInstances.calculateQuorum(5));
        assertEquals(4, CrateInstances.calculateQuorum(6));
        assertEquals(4, CrateInstances.calculateQuorum(7));
        assertEquals(5, CrateInstances.calculateQuorum(8));
        assertEquals(5, CrateInstances.calculateQuorum(9));
        assertEquals(6, CrateInstances.calculateQuorum(10));
        // the quorum must be greater than half of the expected cluster size
        for (int i = 0; i < 1000; i++) {
            assertTrue(CrateInstances.calculateQuorum(i) > i / 2.0f);
        }
    }
}