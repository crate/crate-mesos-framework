package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.CrateState;
import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.config.Configuration;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class CrateRestResourceTest {

    public CrateRestResource resource;

    @Before
    public void setUp() throws Exception {
        Configuration configuration = new Configuration();
        configuration.version("0.48.0");
        PersistentStateStore mockedStore = mock(PersistentStateStore.class);
        when(mockedStore.state()).thenReturn(new CrateState());
        resource = new CrateRestResource(mockedStore, configuration);
    }

    @Test
    public void testIndex() throws Exception {
        UriInfo mockedInfo = mock(UriInfo.class);
        GenericAPIResponse res = resource.index(mockedInfo);
        assertEquals("Crate Mesos Framework", res.getMessage());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterInfo() throws Exception {
        UriInfo mockedInfo = mock(UriInfo.class);
        GenericAPIResponse res = resource.clusterIndex(mockedInfo);
        assertEquals("{resources={heap=256.0, cpus=0.5, disk=1024.0, memory=512.0}, " +
                        "mesosMaster=zk://localhost:2181/mesos, " +
                        "api={apiPort=4040}, " +
                        "cluster={name=crate, httpPort=4200, nodeCount=0, version=0.48.0}, " +
                        "instances={desired=-1, running=0}, " +
                        "excludedSlaves={}}",
                res.getMessage().toString());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResize() throws Exception {
        GenericAPIResponse res = resource.clusterResize(new ClusterResizeRequest(0));
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }
}

