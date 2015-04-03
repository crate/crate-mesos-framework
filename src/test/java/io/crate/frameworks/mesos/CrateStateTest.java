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
        cluster.addInstance(new CrateInstance("127.0.0.1", "1", "0.47.0", 4300, "exec-1", "slave-1"));
        cluster.addInstance(new CrateInstance("127.0.0.2", "2", "0.47.0", 4300, "exec-1", "slave-1"));
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