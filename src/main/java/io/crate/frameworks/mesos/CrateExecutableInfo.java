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


import io.crate.frameworks.mesos.config.Configuration;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;

public class CrateExecutableInfo implements Serializable {

    private final static String CDN_URL = "https://cdn.crate.io/downloads/releases";
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutableInfo.class);

    private final List<URI> downloadURIs;
    private final String nodeNode;
    private final String unicastHosts;
    private final String hostname;   // todo:  this is never used
    private final String execId;
    private final Configuration configuration;
    private final List<Attribute> attributes;

    public CrateExecutableInfo(Configuration configuration,
                               String hostname,
                               CrateInstances crateInstances,
                               List<Attribute> attributes) {
        this.execId = UUID.randomUUID().toString();
        this.hostname = hostname;
        this.configuration = configuration;
        this.attributes = attributes;
        this.downloadURIs = asList(
                URI.create(configuration.versionIsDownloadURL() ?
                        configuration.version :
                        String.format("%s/crate-%s.tar.gz", CDN_URL, configuration.version))
        );
        this.nodeNode = String.format("%s-%s", configuration.clusterName, execId);
        this.unicastHosts = crateInstances.unicastHosts();
    }

    public String nodeName() {
        return nodeNode;
    }

    public int transportPort() {
        return configuration.transportPort;
    }

    public List<String> arguments() {
        List<String> args = new ArrayList<>(asList(
                "crate-*/bin/crate",
                "-p",
                "crate.pid",
                String.format("-Des.cluster.name=%s", configuration.clusterName),
                String.format("-Des.http.port=%d", configuration.httpPort),
                String.format("-Des.transport.tcp.port=%d", configuration.transportPort),
                String.format("-Des.node.name=%s", nodeNode),
                String.format("-Des.discovery.zen.ping.multicast.enabled=%s", "false"),
                String.format("-Des.discovery.zen.ping.unicast.hosts=%s", unicastHosts)
        ));
        if (configuration.dataPath != null) {
            args.add(String.format("-Des.path.data=%s", configuration.dataPath));
        }
        if (configuration.blobPath != null) {
            args.add(String.format("-Des.path.blobs=%s", configuration.blobPath));
        }
        for (Attribute attribute : attributes) {
            if (attribute.hasText()) {
                args.add(String.format("-Des.node.mesos_%s=%s", attribute.getName(), attribute.getText().getValue()));
            }
        }
        for (String crateArg : configuration.crateArgs()) {
            args.add(crateArg);
        }
        return args;
    }

    public List<Environment.Variable> environment() {
        return asList(
                Environment.Variable.newBuilder()
                        .setName("CRATE_HEAP_SIZE")
                        .setValue(String.format("%sm", configuration.resHeap.longValue()))
                .build()
        );
    }

    public Integer httpPort() { return configuration.httpPort; }

    public List<URI> uris() {
        return downloadURIs;
    }

    public File dataDir() {
        return configuration.dataPath == null ? null : new File(configuration.dataPath);
    }

    public File blobDir() {
        return configuration.blobPath == null ? null : new File(configuration.blobPath);
    }

    /**
     * Helper function for Serializable
     * @param value
     * @return
     * @throws IOException
     */
    public static CrateExecutableInfo fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return null;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            CrateExecutableInfo state = (CrateExecutableInfo) objectInputStream.readObject();
            return state;
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not deserialize CrateExecutableInfo:", e);
        }
        return null;
    }

    /**
     * Helper function for Serializable
     * @return
     */
    public byte[] toStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(this);
        } catch (IOException e){
            LOGGER.error("Could not serialize CrateExecutableInfo:", e);
        }
        return out.toByteArray();
    }
}
