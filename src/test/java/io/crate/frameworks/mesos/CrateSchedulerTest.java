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

import io.crate.frameworks.mesos.config.Configuration;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static io.crate.frameworks.mesos.SaneProtos.taskID;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.*;

public class CrateSchedulerTest {

    @Mock
    private SchedulerDriver driver;

    private CrateState state;

    @Mock
    private PersistentStateStore store;

    private Protos.MasterInfo masterInfo;

    @Captor
    private ArgumentCaptor<Collection<Protos.TaskInfo>> taskInfoCaptor;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        masterInfo = Protos.MasterInfo.getDefaultInstance();
        state = new CrateState();
        when(store.state()).thenReturn(state);
    }

    @Test
    public void testThatRegisteredWithInstancesRunning() throws Exception {
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("foo", "1", "0.47.0", 4300, "exec-1", "slave-1"));
        state.desiredInstances(0);
        state.instances(instances);


        CrateScheduler crateScheduler = initScheduler(new Configuration(), "xx");
        assertThat(crateScheduler.reconcileTasks.size(), is(1));
    }

    @Test
    public void testResourceOffersDoesNotSpawnTooManyTasks() throws Exception {
        CrateInstances instances = new CrateInstances();

        state.desiredInstances(4);
        state.instances(instances);

        Protos.FrameworkID frameworkID = Protos.FrameworkID.newBuilder().setValue("xx").build();
        Configuration configuration = new Configuration();
        CrateScheduler crateScheduler = initScheduler(configuration, frameworkID);

        List<Protos.Offer> offers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String idx = Integer.toString(i);
            offers.add(Protos.Offer.newBuilder()
                    .setId(Protos.OfferID.newBuilder().setValue(idx))
                    .setHostname(idx)
                    .setSlaveId(Protos.SlaveID.newBuilder().setValue(idx))
                    .setFrameworkId(frameworkID)
                    .addAllResources(configuration.getAllRequiredResources()).build());
        }

        crateScheduler.resourceOffers(driver, offers);

        verify(driver, times(4)).launchTasks(anyCollectionOf(Protos.OfferID.class), taskInfoCaptor.capture(), any(Protos.Filters.class));
        assertThat(taskInfoCaptor.getValue().size(), is(1));
    }

    @Test
    public void testReconcileTasksWithDifferentVersionAlreadyRunning() throws Exception {
        // configured version should be changed to the version of the running instance
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("foo", "1", "0.47.7", 4300, "exec-1", "slave-1"));
        state.instances(instances);

        Configuration configuration = new Configuration();
        configuration.version("0.48.0");

        CrateScheduler crateScheduler = initScheduler(configuration, "xx");
        crateScheduler.reconcileTasks(driver);
        crateScheduler.statusUpdate(driver,
                Protos.TaskStatus.newBuilder()
                        .setTaskId(taskID("1"))
                        .setState(Protos.TaskState.TASK_RUNNING).build());

        assertThat(configuration.version, is("0.47.7"));
    }

    private CrateScheduler initScheduler(Configuration configuration, String frameworkID) {
        return initScheduler(configuration, Protos.FrameworkID.newBuilder().setValue(frameworkID).build());
    }

    private CrateScheduler initScheduler(Configuration configuration, Protos.FrameworkID frameworkID) {
        CrateScheduler crateScheduler = new CrateScheduler(store, configuration);
        crateScheduler.registered(driver, frameworkID, masterInfo);
        return crateScheduler;
    }

    private static Protos.ExecutorInfo newExecutor(String n) {
        return Protos.ExecutorInfo.newBuilder()
                .setName("Crate Executor")
                .setExecutorId(
                        Protos.ExecutorID.newBuilder()
                                .setValue("newExecutor-" + n)
                                .build()
                )
                .setCommand(Protos.CommandInfo.newBuilder()
                        .setValue(String.format("java -cp crate-mesos.jar io.crate.frameworks.mesos.CrateExecutor"))
                        .build())
                .build();
    }

    @Test
    public void testSlaveExclusion() throws Exception {
        Protos.FrameworkID frameworkID = Protos.FrameworkID.newBuilder().setValue("framework-1").build();
        Configuration configuration = new Configuration();
        CrateScheduler scheduler = initScheduler(configuration, frameworkID);

        CrateInstances instances = new CrateInstances();
        state.desiredInstances(1);
        state.instances(instances);

        Protos.SlaveID salve1 = Protos.SlaveID.newBuilder()
                .setValue("slave-1")
                .build();

        CrateMessage<MessageMissingResource> msg = new CrateMessage<>(CrateMessage.Type.MESSAGE_MISSING_RESOURCE,
                MessageMissingResource.MISSING_DATA_PATH);

        scheduler.frameworkMessage(driver, newExecutor("1").getExecutorId(), salve1, msg.toStream());

        List<Protos.Offer> offers = new ArrayList<>();
        offers.add(Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue("offer1"))
                .setHostname("slave1.crate.io")
                .setSlaveId(salve1)
                .setFrameworkId(frameworkID)
                .addAllResources(configuration.getAllRequiredResources()).build());

        scheduler.resourceOffers(driver, offers);

        final String reason = MessageMissingResource.MISSING_DATA_PATH.reason().toString();
        assertEquals(0, state.crateInstances().size());
        assertEquals(asList("slave-1"), state.excludedSlaveIds());
        assertEquals(asList("slave-1"), state.excludedSlaveIds(reason));
        assertEquals(new HashMap<String, List<String>>() {
            {
                put(reason, asList("slave-1"));
            }
        }, state.excludedSlaves());

        Protos.TaskStatus status1 = Protos.TaskStatus.newBuilder()
                .setSlaveId(salve1)
                .setTaskId(taskID("task-1"))
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();
        scheduler.statusUpdate(driver, status1);

        scheduler.resourceOffers(driver, offers);

        assertEquals(1, state.crateInstances().size());
        assertEquals(Collections.emptyList(), state.excludedSlaveIds());
        assertEquals(Collections.emptyList(), state.excludedSlaveIds(reason));
        assertEquals(new HashMap<String, List<String>>() {
            {
                put(reason, Collections.<String>emptyList());
            }
        }, state.excludedSlaves());
    }
}