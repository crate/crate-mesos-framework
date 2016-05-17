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

import com.google.common.collect.ImmutableList;
import io.crate.frameworks.mesos.config.Configuration;
import org.apache.mesos.Protos;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

public class CrateExecutableInfoTest {

    @Test
    public void testSerializeCrateExecutableInfo() throws Throwable {
        Configuration configuration = new Configuration();
        configuration.clusterName = "foo";
        configuration.dataPath = "/mnt/ssd1/crate";
        configuration.blobPath= "/mnt/data1/crate";
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("host1", "1", "0.48.0", 4300, "exec-1", "slave-1"));
        List<Protos.Attribute> attr = ImmutableList.of(
                Protos.Attribute.newBuilder()
                        .setType(Protos.Value.Type.TEXT)
                        .setName("zone")
                        .setText(Protos.Value.Text.newBuilder().setValue("a").build())
                        .build()
        );
        CrateExecutableInfo info = new CrateExecutableInfo(configuration, "host1", instances, attr, 1);
        byte[] serializedInfo = info.toStream();
        CrateExecutableInfo newInfo = CrateExecutableInfo.fromStream(serializedInfo);
        Protos.Environment.Variable heap = Protos.Environment.Variable.newBuilder()
                .setName("CRATE_HEAP_SIZE")
                .setValue(String.format("%sm", configuration.resHeap.longValue()))
                .build();
        assertThat(newInfo.environment(), Matchers.hasItem(heap));
        assertThat(newInfo.arguments(), Matchers.hasItem("-Des.cluster.name=foo"));
        assertThat(newInfo.arguments(), Matchers.hasItem("-Des.path.data=/mnt/ssd1/crate"));
        assertThat(newInfo.arguments(), Matchers.hasItem("-Des.path.blobs=/mnt/data1/crate"));
        assertThat(newInfo.arguments(), Matchers.hasItem("-Des.node.mesos_zone=a"));
    }

    @Test
    public void testTransportPortIsSetCorrectly() throws Exception {
        Configuration configuration = new Configuration();
        configuration.transportPort = 4250;

        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("runningHost", "1", "0.47.7", 4350, "exec-1", "slave-1"));
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.<Protos.Attribute>of(), 1);
        List<String> args = host1.arguments();

        assertThat(args, Matchers.hasItem("-Des.transport.tcp.port=4250"));
        assertThat(args, Matchers.hasItem("-Des.discovery.zen.ping.unicast.hosts=runningHost:4350"));
    }

    @Test
    public void testClusterSettingsAreSetCorrectly() throws Exception {
        Configuration configuration = new Configuration();

        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("runningHost", "1", "0.54.8", 4300, "exec-1", "slave-1"));
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.<Protos.Attribute>of(), 3);
        List<String> args = host1.arguments();

        assertThat(args, Matchers.hasItem("-Des.discovery.zen.minimum_master_nodes=2"));
        assertThat(args, Matchers.hasItem("-Des.gateway.recover_after_nodes=2"));
        assertThat(args, Matchers.hasItem("-Des.gateway.expected_nodes=3"));
    }

    @Test
    public void testAttributeFromOffersAreSetAsNodeTags() throws Exception {
        Configuration configuration = new Configuration();
        CrateInstances instances = new CrateInstances();
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.of(
                        Protos.Attribute.newBuilder()
                                .setType(Protos.Value.Type.TEXT)
                                .setName("zone")
                                .setText(Protos.Value.Text.newBuilder().setValue("a").build())
                                .build()
                ), 1);
        assertThat(host1.arguments(), Matchers.hasItem("-Des.node.mesos_zone=a"));
    }

    @Test
    public void testCrateArgsAreSet() throws Exception {
        Configuration configuration = new Configuration();
        configuration.crateArgs(Arrays.asList("-Des.foo=x"));
        CrateInstances instances = new CrateInstances();
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.<Protos.Attribute>of(), 1);

        assertThat(host1.arguments(), Matchers.hasItem("-Des.foo=x"));
    }
}