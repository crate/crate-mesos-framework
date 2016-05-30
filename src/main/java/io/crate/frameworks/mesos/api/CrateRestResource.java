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

import com.google.common.base.Splitter;
import io.crate.action.sql.SQLActionException;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.frameworks.mesos.CrateInstances;
import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.Version;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.List;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CrateRestResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateRestResource.class);
    private static final String SQL_MAX_REPLICAS = "SELECT number_of_replicas FROM information_schema.tables GROUP BY number_of_replicas";
    private static final Client RS_CLIENT = ClientBuilder.newClient();
    private final PersistentStateStore store;
    private final Configuration conf;

    public CrateRestResource(PersistentStateStore store, Configuration conf) {
        this.store = store;
        this.conf = conf;
    }

    @Nullable
    private CrateClient client() {
        if (store.state().crateInstances().size() > 0) {
            return new CrateClient(store.state().crateInstances().connectionHosts());
        }
        return null;
    }

    private static int getMaxReplicas(CrateClient client) {
        int maxReplicas = 0;
        try {
            SQLResponse response = client.sql(SQL_MAX_REPLICAS).actionGet();
            for (Object[] objects : response.rows()) {
                List<String> replicas = Splitter.on("-").splitToList((String) objects[0]);
                String val = replicas.get(replicas.size()-1);
                if (replicas.size() == 2 && val.equals("all")) {
                    val = replicas.get(0);
                }
                maxReplicas = Math.max(maxReplicas, Integer.parseInt(val));
            }
        } catch (NoNodeAvailableException e) {
            // since we do not have a crate node to connect to we can accept the request to start up / shut down nodes.
            LOGGER.warn("No Crate node available.", e);
        } catch (SQLActionException e) {
            LOGGER.warn("An error occurred while trying to get max replicas", e);
        }
        return maxReplicas;
    }

    /**
     * Try to set the value of a specific transient cluster setting.
     * Fails silently if no node is available or the database returns a SQL error.
     * @param client An instantiated Crate client instance.
     * @param setting The full qualified setting name.
     * @param value The new value of the setting.
     */
    private void setClusterSetting(CrateClient client, String setting, Object value) {
        LOGGER.info("SET {} = {}", setting, value);
        SQLRequest request = new SQLRequest(
                String.format("SET GLOBAL TRANSIENT \"%s\" = ?", setting),
                new Object[]{ value }
        );
        try {
            client.sql(request).actionGet();
        } catch (SQLActionException | NoNodeAvailableException e) {
            LOGGER.warn("An error occurred while trying to set setting.", e);
        }
    }

    @GET
    public GenericAPIResponse index(@Context UriInfo uriInfo) {
        return new GenericAPIResponse() {
            @Override
            public String getMessage() {
                return String.format("Crate Mesos Framework (%s)", Version.CURRENT);
            }
        };
    }

    @GET
    @Path("/cluster")
    public Response clusterIndex(@Context UriInfo uriInfo) {
        final int desired = store.state().desiredInstances().getValue();
        final int running = store.state().crateInstances().size();
        final HashMap<String, List<String>> excluded = store.state().excludedSlaves();
        return Response.ok().entity(new GenericAPIResponse() {
            @Override
            public Object getMessage() {
                return new HashMap<String, Object>(){
                    {
                        put("mesosMaster", conf.mesosMaster());
                        put("instances", new HashMap<String, Integer>() {
                            {
                                put("desired", desired);
                                put("running", running);
                            }
                        });
                        put("excludedSlaves", excluded);
                        put("cluster", new HashMap<String, Object>(){
                            {
                                put("version", conf.version);
                                put("name", conf.clusterName);
                                put("httpPort", conf.httpPort);
                                put("nodeCount", conf.nodeCount);
                            }
                        });
                        put("resources", new HashMap<String, Double>(){
                            {
                                put("memory", conf.resMemory);
                                put("heap", conf.resHeap);
                                put("cpus", conf.resCpus);
                                put("disk", conf.resDisk);
                            }
                        });
                        put("api", new HashMap<String, Integer>(){
                            {
                                put("apiPort", conf.apiPort);
                            }
                        });
                    }
                };
            }
        }).build();
    }

    @POST
    @Path("/cluster/resize")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response clusterResize(final ClusterResizeRequest data) {
        int desired = data.getInstances();
        if (desired == 0) {
            return Response.status(Response.Status.FORBIDDEN).entity(new GenericAPIResponse() {
                @Override
                public int getStatus() {
                    return Response.Status.FORBIDDEN.getStatusCode();
                }
                @Override
                public Object getMessage() {
                    return "Could not change the number of instances. Scaling down to zero instances is not allowed. Please use '/cluster/shutdown' instead.";
                }
            }).build();
        }

        String address = mesosMasterAddress();
        if (address != null) {
            int activeMesosSlaves = numActiveSlaves(address);
            if (desired > activeMesosSlaves) {
                return Response.status(Response.Status.FORBIDDEN).entity(new GenericAPIResponse() {
                    @Override
                    public int getStatus() {
                        return Response.Status.FORBIDDEN.getStatusCode();
                    }

                    @Override
                    public Object getMessage() {
                        return "Could not initialize more Crate nodes than existing number of mesos agents";
                    }
                }).build();
            }
        }

        CrateClient client = client();
        if (client != null) {
            if (desired < store.state().crateInstances().size()) {
                int maxReplicas = getMaxReplicas(client);
                LOGGER.debug("max replicas = {} desired instances = {}", maxReplicas, desired);
                if (maxReplicas > 0 && desired < maxReplicas + 1) {
                    client.close();
                    return Response.status(Response.Status.FORBIDDEN).entity(new GenericAPIResponse() {
                        @Override
                        public int getStatus() {
                            return Response.Status.FORBIDDEN.getStatusCode();
                        }
                        @Override
                        public Object getMessage() {
                            return "Could not change the number of instances. The number of desired instances is lower than the number of replicas + 1.";
                        }
                    }).build();
                }
            }
            int quorum = CrateInstances.calculateQuorum(desired);
            LOGGER.debug("update cluster settings: desired={} quorum={}", desired, quorum);
            setClusterSetting(client, "discovery.zen.minimum_master_nodes", quorum);
            client.close();
        }
        store.state().desiredInstances(desired);
        return Response.ok(new GenericAPIResponse() {}).build();
    }

    CuratorFramework zkClient() {
        return CuratorFrameworkFactory.builder()
                .retryPolicy(new RetryNTimes(3, 1000))
                .connectString(conf.zookeeper)
                .build();
    }

    @Nullable
    String mesosMasterAddress() {
        try (CuratorFramework cf = zkClient()) {
            cf.start();
            List<String> children = cf.getChildren().forPath("/mesos");
            if (children.isEmpty()) {
                return null;
            }

            JSONObject cfData = null;
            for (String child : children) {
                if (child.startsWith("json.info")) {
                    cfData = new JSONObject(new String(cf.getData().forPath("/mesos/" + child)));
                    break;
                }
            }
            if (cfData != null) {
                JSONObject address = cfData.getJSONObject("address");
                return String.format("%s:%d", address.getString("ip"), address.getInt("port"));
            }
        } catch (Exception e) {
            LOGGER.error("Error while obtaining a mesos address from the curator framework: ", e);
        }
        return null;
    }

    int numActiveSlaves(@Nonnull String mesosAddr) {
        String url = String.format("http://%s/metrics/snapshot", mesosAddr);
        javax.ws.rs.core.Response response = RS_CLIENT.target(url).request(MediaType.APPLICATION_JSON).get();
        JSONObject clusterState = new JSONObject(response.readEntity(String.class));

        return clusterState.getInt("master/slaves_active");
    }

    @POST
    @Path("/cluster/shutdown")
    public Response clusterShutdown() {
        store.state().desiredInstances(0);
        return Response.ok(new GenericAPIResponse() {}).build();
    }

}
