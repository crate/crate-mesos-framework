package io.crate.frameworks.mesos;


import com.google.common.base.Joiner;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.frameworks.mesos.config.Resources;
import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.crate.frameworks.mesos.SaneProtos.taskID;
import static java.util.Arrays.asList;

public class CrateExecutableInfo {

    private final static String CDN_URL = "https://cdn.crate.io/downloads/releases";

    private final String clusterName;
    private final String hostname;
    private final CommandInfo.URI downloadURI;
    private final TaskID taskId;
    private final String nodeNode;
    private final Configuration configuration;
    private final CrateInstances crateInstances;

    public CrateExecutableInfo(Configuration configuration,
                               String hostname,
                               CrateInstances crateInstances) {
        this.configuration = configuration;
        this.crateInstances = crateInstances;
        this.taskId = generateTaskId();
        this.clusterName = configuration.clusterName;
        this.hostname = hostname;
        this.downloadURI = CommandInfo.URI.newBuilder()
                .setValue(String.format("%s/crate-%s.tar.gz", CDN_URL, configuration.version))
                .setExtract(true)
                .build();
        this.nodeNode = String.format("%s-%s", this.clusterName, taskId.getValue());
    }

    private TaskID generateTaskId() {
        return taskID(UUID.randomUUID().toString());
    }

    public String getHostname() {
        return hostname;
    }

    private String unicastHosts() {
        List<String> hosts = new ArrayList<>(crateInstances.size());
        for (CrateInstance crateInstance : crateInstances) {
            hosts.add(String.format("%s:%s", crateInstance.hostname(), crateInstance.transportPort()));
        }
        return Joiner.on(",").join(hosts);
    }

    List<String> genArgs(List<Attribute> attributes) {
        List<String> args = new ArrayList<>(asList(
                "bin/crate",
                String.format("-Des.cluster.name=%s", clusterName),
                String.format("-Des.http.port=%d", configuration.httpPort),
                String.format("-Des.transport.tcp.port=%d", configuration.transportPort),
                String.format("-Des.node.name=%s", nodeNode),
                String.format("-Des.discovery.zen.ping.multicast.enabled=%s", "false"),
                String.format("-Des.discovery.zen.ping.unicast.hosts=%s", unicastHosts())
        ));
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

    public TaskInfo taskInfo(Offer offer, List<Attribute> attributes) {
        assert Resources.matches(offer.getResourcesList(), configuration) :
                "must have enough resources in offer. Otherwise CrateContainer must not be created";

        Environment env = Environment.newBuilder()
                .addAllVariables(Arrays.<Environment.Variable>asList(
                        Environment.Variable.newBuilder()
                                .setName("CRATE_HEAP_SIZE")
                                .setValue(String.format("%sm", configuration.resHeap.longValue()))
                                .build()
                ))
                .build();

        List<String> args = genArgs(attributes);

        // command info
        CommandInfo cmd = CommandInfo.newBuilder()
                .addAllUris(asList(downloadURI))
                .setShell(true)
                .setEnvironment(env)
                .addAllArguments(args)
                .build();

        // create task to run
        TaskInfo.Builder taskBuilder = TaskInfo.newBuilder()
                .setName(clusterName)
                .setTaskId(taskId)
                .setSlaveId(offer.getSlaveId())
                .setCommand(cmd);

        taskBuilder.addAllResources(configuration.getAllRequiredResources());
        return taskBuilder.build();
    }
}
