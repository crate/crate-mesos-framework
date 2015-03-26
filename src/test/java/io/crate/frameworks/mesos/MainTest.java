package io.crate.frameworks.mesos;

import io.crate.frameworks.mesos.config.Configuration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MainTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testParseConfigurationWithProtectedArg() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                "Argument \"-Des.cluster.name\" is protected and managed by the framework. It cannot be set by the user");
        Main.parseConfiguration(new String[]{"--crate-version", "0.47.0", "-Des.cluster.name=foo"});
    }

    @Test
    public void testValidCrateArgsAreSet() throws Exception {
        Configuration configuration = Main.parseConfiguration(new String[]{
                "--crate-version", "0.47.0",
                "-Des.cluster.routing.allocation.awareness.attributes=mesos_zone"
        });
        assertThat(configuration.crateArgs().get(0),
                is("-Des.cluster.routing.allocation.awareness.attributes=mesos_zone"));
    }
}