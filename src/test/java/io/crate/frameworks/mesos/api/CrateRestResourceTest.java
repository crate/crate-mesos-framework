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

import io.crate.frameworks.mesos.CrateInstance;
import io.crate.frameworks.mesos.CrateState;
import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.Version;
import io.crate.frameworks.mesos.config.Configuration;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class CrateRestResourceTest {

    public CrateRestResource resource;

    @Before
    public void setUp() throws Exception {
        Configuration configuration = new Configuration();
        configuration.version("0.48.0");
        PersistentStateStore mockedStore = mock(PersistentStateStore.class);
        CrateState state = new CrateState();
        state.httpPort(44200);
        state.transportPort(44300);
        CrateInstance crate1 = new CrateInstance("crate1", "task-1", "0.48.0",
                state.httpPort(), state.transportPort(), "exec-1", "slave-1");
        state.crateInstances().addInstance(crate1);
        when(mockedStore.state()).thenReturn(state);
        resource = new CrateRestResource(mockedStore, configuration);
    }

    @Test
    public void testIndex() throws Exception {
        UriInfo mockedInfo = mock(UriInfo.class);
        GenericAPIResponse res = resource.index(mockedInfo);
        assertEquals(String.format("Crate Mesos Framework (%s)", Version.CURRENT), res.getMessage());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterInfo() throws Exception {
        UriInfo mockedInfo = mock(UriInfo.class);
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterIndex(mockedInfo).getEntity();
        HashMap<String, Object> entity = (HashMap<String, Object>) res.getMessage();
        assertEquals(entity.get("cluster"), new HashMap<String, Object>(){
            {
                put("version", "0.48.0");
                put("name", "crate");
                put("httpPort", 44200);
                put("transportPort", 44300);
                put("nodeCount", 0);
            }
        });
        assertEquals(entity.get("instances"), new HashMap<String, Integer>() {
            {
                put("desired", -1);
                put("running", 1);
            }
        });
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResize() throws Exception {
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(2)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
        res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(1)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResizeToZeroInstances() throws Exception {
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(0)).getEntity();
        assertEquals("Could not change the number of instances. " +
                "Scaling down to zero instances is not allowed. " +
                "Please use '/cluster/shutdown' instead.", res.getMessage());
        assertEquals(403, res.getStatus());
    }

    @Test
    public void testClusterShutdown() throws Exception {
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterShutdown().getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }
}

