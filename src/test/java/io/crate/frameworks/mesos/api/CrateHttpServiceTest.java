/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

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