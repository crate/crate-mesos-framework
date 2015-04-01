package io.crate.frameworks.mesos.api;

import io.crate.frameworks.mesos.PersistentStateStore;
import io.crate.frameworks.mesos.config.Configuration;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

public class CrateHttpService {

    private final HttpServer server;
    private final static String PACKAGE_NAMESPACE = "io.crate.frameworks.mesos.api";
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateHttpService.class);

    public CrateHttpService(PersistentStateStore crateState, Configuration conf) {
        ResourceConfig httpConf = new ResourceConfig()
                .register(new CrateRestResource(crateState, conf))
                .packages(PACKAGE_NAMESPACE);
        URI httpUri = UriBuilder.fromPath("/").scheme("http").host("0.0.0.0").port(conf.apiPort).build();
        server = GrizzlyHttpServerFactory.createHttpServer(httpUri, httpConf);
        server.getServerConfiguration().addHttpHandler(
                new StaticHttpHandler(getRoot()),
                "/static"
        );
    }

    private static String getRoot() {
        URL url = io.crate.frameworks.mesos.Main.class.getProtectionDomain().getCodeSource().getLocation();
        String jarPath = null;
        try {
            jarPath = URLDecoder.decode(url.getFile(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Could not read root directory path for jar file.", e);
            System.exit(2);
        }

        return new File(jarPath).getParentFile().getPath();
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.shutdown(30_000, TimeUnit.MILLISECONDS);
    }

}

