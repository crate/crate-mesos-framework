package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.CrateState;
import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrateHttpServiceTest {

    public static final int TEST_SERVER_PORT = 40401;

    private CrateHttpService service;
    private Client client;

    @Before
    public void setUp() throws Exception {
        PersistentStateStore store = mock(PersistentStateStore.class);
        CrateState state = new CrateState();
        when(store.state()).thenReturn(state);
        Configuration conf = new Configuration();
        conf.apiPort = TEST_SERVER_PORT;
        service = new CrateHttpService(store, conf);
        service.start();
        client = ClientBuilder.newClient();
    }

    @After
    public void tearDown() throws Exception {
        service.stop();
    }

    private UriBuilder newRequestBuilder(String path) {
        return UriBuilder.fromPath(path).host("localhost").port(TEST_SERVER_PORT).scheme("http");

    }

    @Test
    public void testClusterInfo() throws Exception {
        URI uri = newRequestBuilder("/cluster").build();
        Response res = client.target(uri).request().get();
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResize() throws Exception {
        URI uri = newRequestBuilder("/cluster/resize").build();
        Response res = client.target(uri).request().post(Entity.text("{\"instances\":1}"));
        assertEquals(415, res.getStatus());
        res = client.target(uri).request().post(Entity.json("{\"instances\":1}"));
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testStaticHandler() throws Throwable {
        URI uri = newRequestBuilder("/static/").build();
        Response res = client.target(uri).request().get();
        assertEquals(404, res.getStatus());
        // download path cannot be tested because tests are not running from jar file
    }
}