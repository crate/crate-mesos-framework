package io.crate.frameworks.mesos;


import com.google.common.base.Joiner;
import io.crate.frameworks.mesos.config.Configuration;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.*;

import static java.util.Arrays.asList;

public class CrateExecutableInfo implements Serializable {

    private final static String CDN_URL = "https://cdn.crate.io/downloads/releases";
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateExecutableInfo.class);

    private final URI downloadURI;
    private final String nodeNode;
    private final String unicastHosts;
    private final String hostname;
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
        this.downloadURI = URI.create(String.format("%s/crate-%s.tar.gz", CDN_URL, configuration.version));
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
                "bin/crate",
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

    public URI uri() {
        return downloadURI;
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
