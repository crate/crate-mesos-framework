package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.PersistentStateStore;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class CrateRestResourceTest {

    public CrateRestResource resource;

    @Before
    public void setUp() throws Exception {
        PersistentStateStore mockedStore = mock(PersistentStateStore.class);
        resource = new CrateRestResource(mockedStore);
    }

    @Test
    public void testIndex() throws Exception {
        UriInfo mockedInfo = mock(UriInfo.class);
        GenericAPIResponse res = resource.index(mockedInfo);
        assertEquals("Crate Mesos Framework", res.getMessage());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResize() throws Exception {
        GenericAPIResponse res = resource.clusterResize(new ClusterResizeRequest(0));
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }
}

