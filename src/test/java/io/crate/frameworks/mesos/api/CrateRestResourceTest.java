package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.CrateState;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class CrateRestResourceTest {

    public CrateRestResource resource;

    @Before
    public void setUp() throws Exception {
        CrateState mockedState = mock(CrateState.class);
        resource = new CrateRestResource(mockedState);
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
        GenericAPIResponse res = resource.clusterResize(new ClusterInfo(0));
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }
}

