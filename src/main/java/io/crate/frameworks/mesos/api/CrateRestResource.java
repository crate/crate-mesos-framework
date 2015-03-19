package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.PersistentStateStore;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CrateRestResource {

    private final PersistentStateStore store;

    public CrateRestResource(PersistentStateStore store) {
        this.store = store;
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
    public GenericAPIResponse clusterInfo(@Context UriInfo uriInfo) {
        final int desired = store.state().desiredInstances().getValue();
        final int running = store.state().crateInstances().size();
        return new GenericAPIResponse() {
            @Override
            public Object getMessage() {
                return new HashMap<String, Integer>(){
                    {
                        put("desiredInstances", desired);
                        put("runningInstances", running);
                    }
                };
            }
        };
    }

    @POST
    @Path("/cluster/resize")
    @Consumes(MediaType.APPLICATION_JSON)
    public GenericAPIResponse clusterResize(final ClusterResizeRequest data) {
        this.store.desiredInstances(data.getInstances());
        return new GenericAPIResponse() {};
    }

}

