package io.crate.frameworks.mesos;

import io.crate.frameworks.mesos.config.Configuration;
import org.hamcrest.Matchers;
import org.junit.Test;

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
        List<String> args = host1.genArgs();

        assertThat(args, Matchers.hasItem("-Des.transport.tcp.port=4250"));
        assertThat(args, Matchers.hasItem("-Des.discovery.zen.ping.unicast.hosts=runningHost:4350"));
    }
}