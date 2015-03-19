package io.crate.frameworks.mesos.api;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import io.crate.frameworks.mesos.CrateState;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class CrateHttpService {

    private final static int HTTP_PORT = 4040;
    private final static URI HTTP_URI = URI.create(String.format("http://0.0.0.0:%d/", HTTP_PORT));

    private final HttpServer server;

    public CrateHttpService(CrateState crateState) {
        ResourceConfig httpConf = new ResourceConfig()
                .register(new CrateRestResource(crateState))
                .packages("io.crate.frameworks.mesos.api");
        server = GrizzlyHttpServerFactory.createHttpServer(HTTP_URI, httpConf);
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

