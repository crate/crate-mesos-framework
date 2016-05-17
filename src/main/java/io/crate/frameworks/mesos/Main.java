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

package io.crate.frameworks.mesos;

import com.beust.jcommander.JCommander;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import io.crate.frameworks.mesos.api.CrateHttpService;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.shade.org.apache.commons.lang3.ObjectUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.state.ZooKeeperState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final String JAVA_URL = "https://cdn.crate.io/downloads/openjdk/jre-7u80-linux.tar.gz";

    private static final Set<String> HELP_OPTIONS = Sets.newHashSet("-h", "--help", "help");
    private static final Set<String> PROTECTED_CRATE_ARGS = Sets.newHashSet(
            "-Des.cluster.name",
            "-Des.http.port",
            "-Des.transport.tcp.port",
            "-Des.node.name",
            "-Des.discovery.zen.ping.multicast.enabled",
            "-Des.discovery.zen.ping.unicast.hosts",
            "-Des.path.data",
            "-Des.path.blobs",
            "-Des.path.logs"
    );

    static Configuration parseConfiguration(String[] args) {
        Configuration configuration = new Configuration();
        JCommander jCommander;

        List<String> crateArgs = new ArrayList<>();
        List<String> safeArgs = new ArrayList<>(args.length);
        for (String arg : args) {
            if (HELP_OPTIONS.contains(arg)) {
                jCommander = new JCommander(configuration);
                jCommander.usage();
                System.exit(1);
            }
            if (arg.startsWith("-Des.")) {
                String argKey = arg.split("\\=")[0];
                if (PROTECTED_CRATE_ARGS.contains(argKey)) {
                    throw new IllegalArgumentException(
                            String.format("Argument \"%s\" is protected and managed by the framework. " +
                                    "It cannot be set by the user", argKey));
                } else {
                    crateArgs.add(arg);
                }
            } else {
                safeArgs.add(arg);
            }
        }
        // todo:  jCommander below is never used, if removed safeArgs is never used.  It is unclear of it's purpose
        jCommander = new JCommander(configuration, safeArgs.toArray(new String[safeArgs.size()]));     // todo:  jcommander is never used
        configuration.crateArgs(crateArgs);
        LOGGER.debug("args: {}", configuration);
        return configuration;
    }


    private static Optional<Protos.Credential> readCredentials() {
        if (System.getenv("MESOS_AUTHENTICATE") != null) {
            LOGGER.debug("Enabling authentication for the framework");
            final String principal = System.getenv("DEFAULT_PRINCIPAL");
            final String secret = System.getenv("DEFAULT_SECRET");
            if (principal == null) {
                LOGGER.error("Expecting authentication principal in the environment");
                System.exit(1);
            }
            Protos.Credential.Builder credential = Protos.Credential.newBuilder().setPrincipal(principal);
            if (secret == null) {
                LOGGER.error("Expecting authentication secret in the environment");
            } else {
                credential.setSecret(secret);
            }
            return Optional.of(credential.build());
        } else {
            return Optional.absent();
        }
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        Configuration configuration = parseConfiguration(args);

        final double frameworkFailoverTimeout = 31536000d; // 60 * 60 * 24 * 365 = 1y

        final String webUri = UriBuilder.fromPath("/cluster")
                .scheme("http")
                .host(host())
                .port(configuration.apiPort)
                .build().toString();
        Protos.FrameworkInfo.Builder frameworkBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(configuration.frameworkName)
                .setUser(configuration.user)
                .setRole(configuration.role)
                .setWebuiUrl(webUri)
                .setCheckpoint(true) // will be enabled by default in Mesos 0.22
                .setFailoverTimeout(frameworkFailoverTimeout);

        PersistentStateStore stateStore = new PersistentStateStore(
                new ZooKeeperState(configuration.zookeeper, 20_000, TimeUnit.MILLISECONDS,
                        String.format("/%s/%s", configuration.frameworkName, configuration.clusterName)),
                configuration.nodeCount);

        Optional<String> frameworkId = stateStore.state().frameworkId();
        if (frameworkId.isPresent()) {
            frameworkBuilder.setId(Protos.FrameworkID.newBuilder().setValue(frameworkId.get()).build());
        }

        final Scheduler scheduler = new CrateScheduler(stateStore, configuration);

        // create the driver
        MesosSchedulerDriver driver;

        String mesosMaster = configuration.mesosMaster();
        Optional<Protos.Credential> credential = readCredentials();
        if (credential.isPresent()) {
            frameworkBuilder.setPrincipal(credential.get().getPrincipal());
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster, credential.get());
        } else {
            frameworkBuilder.setPrincipal("crate-framework");
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster);
        }

        CrateHttpService api = new CrateHttpService(stateStore, configuration);
        api.start();
        int status = driver.run() == Protos.Status.DRIVER_STOPPED ? 0 : 1;

        // Ensure that the driver process terminates.
        api.stop();
        driver.stop();
        System.exit(status);
    }

    public static String host() {
        return ObjectUtils.firstNonNull(System.getenv("LIBPROCESS_IP"),
                System.getenv("HOST"),
                System.getenv("MESOS_HOSTNAME"),
                currentHost());
    }

    private static String currentHost() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not obtain hostname. Using localhost", e);
            host = "127.0.0.1";
        }
        return host;
    }
}
