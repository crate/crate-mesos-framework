package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.config.ApiConfiguration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import io.crate.frameworks.mesos.CrateState;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class CrateHttpService {

    private final HttpServer server;

    public CrateHttpService(CrateState crateState, ApiConfiguration config) {
        ResourceConfig httpConf = new ResourceConfig()
                .register(new CrateRestResource(crateState))
                .packages("io.crate.frameworks.mesos.api");
        int http_port = config.apiPort();
        URI httpUri = URI.create(String.format("http://0.0.0.0:%d/", http_port));
        server = GrizzlyHttpServerFactory.createHttpServer(httpUri, httpConf);
    }


    public void start() throws IOException {
        server.start();
    }

    public boolean isRunning() {
        return server.isStarted();
    }

    public void stop() {
        server.shutdown(30_000, TimeUnit.MILLISECONDS);
    }

}

