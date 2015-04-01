package io.crate.frameworks.mesos.api;

import com.google.common.base.Splitter;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.config.Configuration;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.HashMap;
import java.util.List;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CrateRestResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateRestResource.class);
    private static final String SHARD_COUNT_STMT = "select _node['id'] as id, _node['name'] as name, count(*) as shards from sys.shards group by _node['id'], _node['name'] order by 3 desc";
    private final PersistentStateStore store;
    private final Configuration conf;

    public CrateRestResource(PersistentStateStore store, Configuration conf) {
        this.store = store;
        this.conf = conf;
    }

    private CrateClient client() {
        if (store.state().crateInstances().size() > 0) {
            return new CrateClient(store.state().crateInstances().unicastHosts());
        }
        return null;
    }

    @GET
    public GenericAPIResponse index(@Context UriInfo uriInfo) {
        return new GenericAPIResponse() {
            @Override
            public String getMessage() {
                return "Crate Mesos Framework";
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
        CrateClient client = client();
        if (desired < store.state().crateInstances().size() && client != null) {
            int maxReplicas = 0;
            try {
                SQLResponse response = client.sql("select number_of_replicas from information_schema.tables group by number_of_replicas").actionGet();
                for (Object[] objects : response.rows()) {
                    List<String> replicas = Splitter.on("-").splitToList((String) objects[0]);
                    String val = replicas.get(replicas.size()-1);
                    if (replicas.size() == 2 && val.equals("all")) {
                        val = replicas.get(0);
                    }
                    maxReplicas = Math.max(maxReplicas, Integer.valueOf(val));
                }
            } catch (NoNodeAvailableException e) {
                // since we do not have a crate node to connect to we can accept the request to start up / shut down nodes.
                LOGGER.error("No Crate node available.", e);
            }
            LOGGER.debug("max replicas = {} desired instances = {}", maxReplicas, desired);
            client.close();
            if (maxReplicas > 0 && desired < maxReplicas + 1) {
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
        store.state().desiredInstances(desired);
        return Response.ok(new GenericAPIResponse() {}).build();
    }

}

