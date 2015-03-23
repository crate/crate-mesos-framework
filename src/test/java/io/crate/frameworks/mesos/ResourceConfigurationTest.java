package io.crate.frameworks.mesos;

import io.crate.frameworks.mesos.config.ResourceConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static io.crate.frameworks.mesos.SaneProtos.cpus;
import static io.crate.frameworks.mesos.SaneProtos.mem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ResourceConfigurationTest {

    private ResourceConfiguration resourceConfiguration;

    @Before
    public void setUp() throws Exception {
        resourceConfiguration = new ResourceConfiguration(
                2,
                1024 * 8,
                1024 * 4,
                1024 * 1024 * 20);
    }

    @Test
    public void testMatchesWithOfferThatHasToFewCpus() throws Exception {
        assertThat(resourceConfiguration.matches(Arrays.asList(cpus(1), mem(20_000))), is(false));
    }

    @Test
    public void testMatchesWithOfferThatHasNotEnoughMemory() throws Exception {
        assertThat(resourceConfiguration.matches(Arrays.asList(cpus(4), mem(512))), is(false));
    }

    @Test
    public void testMatchesWithOfferThatHasEnough() throws Exception {
        assertThat(resourceConfiguration.matches(Arrays.asList(cpus(4), mem(20_000))), is(true));
    }

}