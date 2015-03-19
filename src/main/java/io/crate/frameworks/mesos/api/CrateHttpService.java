package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.config.ApiConfiguration;
import io.crate.frameworks.mesos.PersistentStateStore;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class CrateHttpService {

    private final HttpServer server;
    private final static String PACKAGE_NAMESPACE = "io.crate.frameworks.mesos.api";

    public CrateHttpService(PersistentStateStore crateState, ApiConfiguration config) {
        ResourceConfig httpConf = new ResourceConfig()
                .register(new CrateRestResource(crateState))
                .packages(PACKAGE_NAMESPACE);
        URI httpUri = URI.create(String.format("http://0.0.0.0:%d/", config.apiPort()));
        server = GrizzlyHttpServerFactory.createHttpServer(httpUri, httpConf);
    }


    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.shutdown(30_000, TimeUnit.MILLISECONDS);
    }

}

