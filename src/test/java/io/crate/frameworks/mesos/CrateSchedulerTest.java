package io.crate.frameworks.mesos;

import io.crate.frameworks.mesos.config.ClusterConfiguration;
import io.crate.frameworks.mesos.config.ResourceConfiguration;
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

    @Mock
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
    }

    @Test
    public void testThatRegisteredWithInstancesRunning() throws Exception {
        CrateInstances instances = new CrateInstances();
        instances.addInstance(new CrateInstance("foo", "1"));
        when(store.state()).thenReturn(state);
        when(store.state().crateInstances()).thenReturn(instances);
        when(store.state().desiredInstances()).thenReturn(new Observable<Integer>(0));
        CrateScheduler crateScheduler = new CrateScheduler(
                store,
                ResourceConfiguration.fromEnvironment(),
                ClusterConfiguration.fromEnvironment());

        crateScheduler.registered(driver, Protos.FrameworkID.getDefaultInstance(), masterInfo);
        assertThat(crateScheduler.reconcileTasks.size(), is(1));
    }


    @Test
    public void testResourceOffersDoesNotSpawnTooManyTasks() throws Exception {
        CrateInstances instances = new CrateInstances();

        when(store.state()).thenReturn(state);
        when(store.state().crateInstances()).thenReturn(instances);
        when(store.state().desiredInstances()).thenReturn(new Observable<Integer>(4));

        Protos.FrameworkID frameworkID = Protos.FrameworkID.newBuilder().setValue("xx").build();

        ResourceConfiguration resourceConfiguration = ResourceConfiguration.fromEnvironment();
        ClusterConfiguration clusterConfiguration = ClusterConfiguration.fromEnvironment();

        CrateScheduler crateScheduler = new CrateScheduler(store, resourceConfiguration, clusterConfiguration);
        crateScheduler.registered(driver, frameworkID, masterInfo);

        List<Protos.Offer> offers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String idx = Integer.toString(i);
            offers.add(Protos.Offer.newBuilder()
                    .setId(Protos.OfferID.newBuilder().setValue(idx))
                    .setHostname(idx)
                    .setSlaveId(Protos.SlaveID.newBuilder().setValue(idx))
                    .setFrameworkId(frameworkID)
                    .addAllResources(resourceConfiguration.getAllRequiredResources()).build());
        }

        crateScheduler.resourceOffers(driver, offers);

        verify(driver).launchTasks(anyCollectionOf(Protos.OfferID.class), taskInfoCaptor.capture(), any(Protos.Filters.class));
        assertThat(taskInfoCaptor.getValue().size(), is(4));
    }
}