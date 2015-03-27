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
        instances.addInstance(new CrateInstance("host1", "1", "0.48.0", 4300));
        List<Protos.Attribute> attr = ImmutableList.of(
                Protos.Attribute.newBuilder()
                        .setType(Protos.Value.Type.TEXT)
                        .setName("zone")
                        .setText(Protos.Value.Text.newBuilder().setValue("a").build())
                        .build()
        );
        CrateExecutableInfo info = new CrateExecutableInfo(configuration, "host1", instances, attr);
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
        instances.addInstance(new CrateInstance("runningHost", "1", "0.47.7", 4350));
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.<Protos.Attribute>of());
        List<String> args = host1.arguments();

        assertThat(args, Matchers.hasItem("-Des.transport.tcp.port=4250"));
        assertThat(args, Matchers.hasItem("-Des.discovery.zen.ping.unicast.hosts=runningHost:4350"));
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
                ));
        assertThat(host1.arguments(), Matchers.hasItem("-Des.node.mesos_zone=a"));
    }

    @Test
    public void testCrateArgsAreSet() throws Exception {
        Configuration configuration = new Configuration();
        configuration.crateArgs(Arrays.asList("-Des.foo=x"));
        CrateInstances instances = new CrateInstances();
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances,
                ImmutableList.<Protos.Attribute>of());

        assertThat(host1.arguments(), Matchers.hasItem("-Des.foo=x"));
    }
}