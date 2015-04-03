package io.crate.frameworks.mesos.config;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static io.crate.frameworks.mesos.SaneProtos.cpus;
import static io.crate.frameworks.mesos.SaneProtos.mem;
import static io.crate.frameworks.mesos.SaneProtos.ports;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ResourcesTest {

    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        configuration = new Configuration();
        configuration.resCpus = 2.0d;
        configuration.resMemory = 1024d * 8;
        configuration.resHeap = 1024d * 4;
        configuration.resDisk = 1024d * 20;
        configuration.httpPort = 4200;
    }

    @Test
    public void testMatchesWithOfferThatHasToFewCpus() throws Exception {
        assertThat(Resources.matches(Arrays.asList(cpus(1), mem(20_000)), configuration), is(false));
    }

    @Test
    public void testMatchesWithOfferThatHasNotEnoughMemory() throws Exception {
        assertThat(Resources.matches(Arrays.asList(cpus(4), mem(512)), configuration), is(false));
    }

    @Test
    public void testMatchesWithOfferThatHasNoRequestedPorts() throws Exception {
        assertThat(Resources.matches(Arrays.asList(cpus(4), mem(512), ports(3000, 4000)), configuration), is(false));
    }

    @Test
    public void testMatchesWithOfferThatHasEnough() throws Exception {
        assertThat(Resources.matches(Arrays.asList(cpus(4), mem(20_000), ports(4000, 5000)), configuration), is(true));
    }

    @Test
    public void testMatchesWithOfferThatHasEnoughEq() throws Exception {
        assertThat(Resources.matches(Arrays.asList(
                cpus(4),
                mem(20_000),
                ports(4200, 4200),
                ports(4300, 4300)), configuration), is(true));
    }
}