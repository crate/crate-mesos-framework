package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.config.Configuration;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CrateRestResource {

    private final PersistentStateStore store;
    private final Configuration conf;

    public CrateRestResource(PersistentStateStore store, Configuration conf) {
        this.store = store;
        this.conf = conf;
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
    public GenericAPIResponse clusterIndex(@Context UriInfo uriInfo) {
        final int desired = store.state().desiredInstances().getValue();
        final int running = store.state().crateInstances().size();
        return new GenericAPIResponse() {
            @Override
            public Object getMessage() {
                return new HashMap<String, Object>(){
                    {
                        put("mesosMaster", conf.mesosMaster());
                        put("cluster", new HashMap<String, Object>(){
                            {
                                put("version", conf.version());
                                put("clusterName", conf.clusterName());
                                put("httpPort", conf.httpPort());
                                put("desiredInstances", desired);
                                put("runningInstances", running);
                                put("nodeCount", conf.nodeCount());
                            }
                        });
                        put("resources", new HashMap<String, Double>(){
                            {
                                put("memory", conf.resourcesMemory());
                                put("heap", conf.resourcesHeap());
                                put("cpus", conf.resourcesCpus());
                                put("disk", conf.resourcesDisk());
                            }
                        });
                        put("api", new HashMap<String, Integer>(){
                            {
                                put("apiPort", conf.apiPort());
                            }
                        });
                    }
                };
            }
        };
    }

    @POST
    @Path("/cluster/resize")
    @Consumes(MediaType.APPLICATION_JSON)
    public GenericAPIResponse clusterResize(final ClusterResizeRequest data) {
        this.store.state().desiredInstances(data.getInstances());
        return new GenericAPIResponse() {};
    }

}

