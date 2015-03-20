package io.crate.frameworks.mesos;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrateSchedulerTest {


    @Test
    public void testThatRegisteredWithInstancesRunning() throws Exception {
        CrateState state = mock(CrateState.class);
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("foo", "1"));

        when(state.crateInstances()).thenReturn(instances);

        CrateScheduler crateScheduler = new CrateScheduler(
                state,
                ResourceConfiguration.fromEnvironment()
        );

        SchedulerDriver driver = mock(SchedulerDriver.class);
        Protos.MasterInfo masterInfo = Protos.MasterInfo.getDefaultInstance();
        crateScheduler.registered(driver, Protos.FrameworkID.getDefaultInstance(), masterInfo);

        assertThat(crateScheduler.reconcileTasks.size(), is(1));
    }
}