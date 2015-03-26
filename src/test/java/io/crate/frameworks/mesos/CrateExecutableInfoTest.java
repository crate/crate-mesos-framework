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
    public void testTransportPortIsSetCorrectly() throws Exception {
        Configuration configuration = new Configuration();
        configuration.transportPort = 4250;

        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("runningHost", "1", "0.47.7", 4350));
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances);
        List<String> args = host1.genArgs(ImmutableList.<Protos.Attribute>of());

        assertThat(args, Matchers.hasItem("-Des.transport.tcp.port=4250"));
        assertThat(args, Matchers.hasItem("-Des.discovery.zen.ping.unicast.hosts=runningHost:4350"));
    }

    @Test
    public void testAttributeFromOffersAreSetAsNodeTags() throws Exception {
        Configuration configuration = new Configuration();
        CrateInstances instances = new CrateInstances();

        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances);
        List<String> args = host1.genArgs(ImmutableList.of(
                Protos.Attribute.newBuilder()
                        .setType(Protos.Value.Type.TEXT)
                        .setName("zone")
                        .setText(Protos.Value.Text.newBuilder().setValue("a").build())
                .build()
        ));

        assertThat(args, Matchers.hasItem("-Des.node.mesos_zone=a"));
    }

    @Test
    public void testCrateArgsAreSet() throws Exception {
        Configuration configuration = new Configuration();
        configuration.crateArgs(Arrays.asList("-Des.foo=x"));
        CrateInstances instances = new CrateInstances();
        CrateExecutableInfo host1 = new CrateExecutableInfo(configuration, "host1", instances);
        List<String> args = host1.genArgs(ImmutableList.<Protos.Attribute>of());

        assertThat(args, Matchers.hasItem("-Des.foo=x"));
    }
}