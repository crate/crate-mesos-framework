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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        instances.addInstance(new CrateInstance("foo", "1", "0.47.0"));
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

        verify(driver).launchTasks(anyCollectionOf(Protos.OfferID.class), taskInfoCaptor.capture(), any(Protos.Filters.class));
        assertThat(taskInfoCaptor.getValue().size(), is(4));
    }

    @Test
    public void testReconcileTasksWithDifferentVersionAlreadyRunning() throws Exception {
        // configured version should be changed to the version of the running instance
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("foo", "1", "0.47.7"));
        state.instances(instances);

        Configuration configuration = new Configuration();
        configuration.version("0.48.0");

        CrateScheduler crateScheduler = initScheduler(configuration, "xx");
        crateScheduler.reconcileTasks(driver);

        assertThat(configuration.version(), is("0.47.7"));
    }

    private CrateScheduler initScheduler(Configuration configuration, String frameworkID) {
        return initScheduler(configuration, Protos.FrameworkID.newBuilder().setValue(frameworkID).build());
    }

    private CrateScheduler initScheduler(Configuration configuration, Protos.FrameworkID frameworkID) {
        CrateScheduler crateScheduler = new CrateScheduler(store, configuration);
        crateScheduler.registered(driver, frameworkID, masterInfo);
        return crateScheduler;
    }
}