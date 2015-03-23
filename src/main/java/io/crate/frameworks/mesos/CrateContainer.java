package io.crate.frameworks.mesos;


import com.google.common.base.Joiner;
import io.crate.frameworks.mesos.config.Configuration;
import io.crate.frameworks.mesos.config.Resources;
import org.apache.mesos.Protos.*;

import java.util.*;

import static io.crate.frameworks.mesos.SaneProtos.taskID;
import static java.util.Arrays.asList;

public class CrateContainer {

    private final static String REPO = "crate";
    private final static String CMD = "crate";

    public final static int TRANSPORT_PORT = 4300;

    private final Collection<String> occupiedHosts;
    private final String clusterName;

    private final String hostname;
    private final String imageName;
    private final TaskID taskId;
    private final String nodeNode;
    private final Configuration configuration;

    public CrateContainer(Configuration configuration,
                          String hostname,
                          Collection<String> occupiedHosts) {
        UUID id = UUID.randomUUID();
        this.configuration = configuration;
        this.occupiedHosts = occupiedHosts;
        this.taskId = taskID(id.toString());
        this.clusterName = configuration.clusterName();
        this.hostname = hostname;
        this.imageName = String.format("%s:%s", REPO, configuration.version());
        this.nodeNode = String.format("%s-%s", this.clusterName, id);
    }

    public String getHostname() {
        return hostname;
    }

    private String unicastHosts() {
        List<String> hosts = new ArrayList<>();
        for (String occupiedHost : occupiedHosts) {
            hosts.add(String.format("%s:%s", occupiedHost, TRANSPORT_PORT));
        }
        return Joiner.on(",").join(hosts);
    }

    public TaskInfo taskInfo(Offer offer) {
        assert Resources.matches(offer.getResourcesList(), configuration) :
                "must have enough resources in offer. Otherwise CrateContainer must not be created";

        Environment env = Environment.newBuilder()
                .addAllVariables(Arrays.<Environment.Variable>asList(
                        Environment.Variable.newBuilder()
                                .setName("CRATE_HEAP_SIZE")
                                .setValue(String.format("%sm", configuration.resourcesHeap().longValue()))
                                .build()
                ))
                .build();

        // docker image info
        ContainerInfo.DockerInfo dockerInfo = ContainerInfo.DockerInfo.newBuilder()
                .setImage(imageName)
                .setNetwork(ContainerInfo.DockerInfo.Network.HOST)
                .setPrivileged(true)
                .build();

        // container info
        ContainerInfo containerInfo = ContainerInfo.newBuilder()
                .setType(ContainerInfo.Type.DOCKER)
                .setDocker(dockerInfo)
                .build();

        // command info
        CommandInfo cmd = CommandInfo.newBuilder()
                .setShell(false)
                .setEnvironment(env)
                .addAllArguments(asList(CMD,
                        String.format("-Des.cluster.name=%s", clusterName),
                        String.format("-Des.http.port=%d", configuration.httpPort()),
                        String.format("-Des.node.name=%s", nodeNode),
                        String.format("-Des.discovery.zen.ping.multicast.enabled=%s", "false"),
                        String.format("-Des.discovery.zen.ping.unicast.hosts=%s", unicastHosts())
                ))
                .build();

        // create task to run
        TaskInfo.Builder taskBuilder = TaskInfo.newBuilder()
                .setName("task " + taskId.getValue())
                .setTaskId(taskId)
                .setSlaveId(offer.getSlaveId())
                .setContainer(containerInfo)
                .setCommand(cmd);

        taskBuilder.addAllResources(configuration.getAllRequiredResources());
        return taskBuilder.build();
    }
}
