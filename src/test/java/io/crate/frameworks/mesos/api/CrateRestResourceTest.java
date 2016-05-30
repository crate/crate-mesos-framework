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
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CrateRestResourceTest {

    private CrateRestResource resource;

    private CuratorFramework cf = mock(CuratorFramework.class);
    private GetDataBuilder dataBuilder = mock(GetDataBuilder.class);
    private GetChildrenBuilder childBuilder = mock(GetChildrenBuilder.class);

    @Before
    public void setUp() throws Exception {
        Configuration configuration = new Configuration();
        configuration.version("0.48.0");
        PersistentStateStore mockedStore = mock(PersistentStateStore.class);
        CrateState state = new CrateState();
        CrateInstance crate1 = new CrateInstance("crate1", "task-1", "0.48.0", 44300, "exec-1", "slave-1");
        state.crateInstances().addInstance(crate1);
        when(mockedStore.state()).thenReturn(state);

        resource = spy(new CrateRestResource(mockedStore, configuration));

        when(cf.getChildren()).thenReturn(childBuilder);
        when(cf.getData()).thenReturn(dataBuilder);
        when(resource.zkClient()).thenReturn(cf);

        doReturn(Integer.MAX_VALUE).when(resource).numActiveSlaves((JSONObject) any());
        doReturn(new JSONObject()).when(resource).mesosMasterAddress();
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
                put("httpPort", 4200);
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
    public void testClusterResizeNumInstancesEqualOrLessThanActiveAgents() throws Exception {
        doReturn(3).when(resource).numActiveSlaves((JSONObject) any());

        GenericAPIResponse res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(2)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());

        res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(3)).getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }

    @Test
    public void testClusterResizeNumInstancesGreaterThanActiveAgents() throws Exception {
        doReturn(3).when(resource).numActiveSlaves((JSONObject) any());

        GenericAPIResponse res = (GenericAPIResponse) resource.clusterResize(new ClusterResizeRequest(4)).getEntity();
        assertEquals("Could not initialize more Crate nodes than existing number of mesos agents", res.getMessage());
        assertEquals(403, res.getStatus());
    }

    @Test
    public void testMasterMesosAddress() throws Exception {
        when(childBuilder.forPath(anyString())).thenReturn(Arrays.asList("json.info_0000000000"));
        when(dataBuilder.forPath(anyString())).thenReturn("{\"address\":{\"ip\":\"host\",\"port\":5050}}".getBytes());
        doCallRealMethod().when(resource).mesosMasterAddress();

        JSONObject address = resource.mesosMasterAddress();
        assertThat(address.getString("ip"), is("host"));
        assertThat(address.getInt("port"), is(5050));
    }

    @Test
    public void testMasterMesosAddressNoAddressKeyInCFJsonData() throws Exception {
        when(childBuilder.forPath(anyString())).thenReturn(Arrays.asList("json.info_0000000000"));
        when(dataBuilder.forPath(anyString())).thenReturn("{\"a\":{\"ip\":\"172.17.0.3\",\"port\":5050}}".getBytes());
        doCallRealMethod().when(resource).mesosMasterAddress();

        JSONObject address = resource.mesosMasterAddress();
        assertThat(address, is(nullValue()));
    }

    @Test
    public void testMasterMesosAddressChildrenListIsEmpty() throws Exception {
        when(childBuilder.forPath(anyString())).thenReturn(Collections.<String>emptyList());
        doCallRealMethod().when(resource).mesosMasterAddress();

        JSONObject address = resource.mesosMasterAddress();
        assertThat(address, is(nullValue()));
    }

    @Test
    public void testClusterShutdown() throws Exception {
        GenericAPIResponse res = (GenericAPIResponse) resource.clusterShutdown().getEntity();
        assertEquals("SUCCESS", res.getMessage());
        assertEquals(200, res.getStatus());
    }

}

