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
        CrateInstance crate1 = new CrateInstance("crate1", "task-1", "0.48.0", 44300);
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
        assertEquals("{resources={heap=256.0, cpus=0.5, disk=1024.0, memory=512.0}, " +
                        "mesosMaster=zk://localhost:2181/mesos, " +
                        "api={apiPort=4040}, " +
                        "cluster={name=crate, httpPort=4200, nodeCount=0, version=0.48.0}, " +
                        "instances={desired=-1, running=1}, " +
                        "excludedSlaves={}}",
                res.getMessage().toString());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResize() throws Exception {
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(2)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
        res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(0)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }
}

