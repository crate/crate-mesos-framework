package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.CrateState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CrateRestResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateRestResource.class);
    private final CrateState state;

    public CrateRestResource(CrateState state) {
        this.state = state;
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
        final int instances = state.desiredInstances();
        return new GenericAPIResponse() {
            @Override
            public Object getMessage() {
                return new ClusterInfo(instances);
            }
        };
    }

    @POST
    @Path("/cluster/resize")
    @Consumes(MediaType.APPLICATION_JSON)
    public GenericAPIResponse clusterResize(final ClusterInfo data) {
        this.state.desiredInstances(data.getInstances());
        return new GenericAPIResponse() {};
    }

}

