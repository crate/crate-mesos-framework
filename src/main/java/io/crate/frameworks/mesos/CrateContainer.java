package io.crate.frameworks.mesos;


import com.google.common.base.Joiner;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.crate.frameworks.mesos.SaneProtos.taskID;

public class CrateContainer {

    private final static String REPO = "crate";
    private final static String CMD = "crate";

    private final static int HTTP_PORT = 4200;
    private final static int TRANSPORT_PORT = 4300;

    private final String version = "latest";
    private final Integer id;
    private final Collection<String> occupiedHosts;
    private final String clusterName;

    private final String hostname;
    private final String imageName;
    private final TaskID taskId;
    private final String nodeNode;

    public CrateContainer(Integer id, String clusterName, String hostname, Collection<String> occupiedHosts) {
        this.id = id;
        this.occupiedHosts = occupiedHosts;
        this.taskId = taskID(Integer.toString(id));
        this.clusterName = clusterName;
        this.hostname = hostname;
        this.imageName = String.format("%s:%s", REPO, this.version);
        this.nodeNode = String.format("%s%s", this.clusterName, this.id);
    }

    public String getHostname() {
        return hostname;
    }

    public TaskID taskId() {
        return taskId;
    }

    private String unicastHosts() {
        List<String> hosts = new ArrayList<>();
        for (String occupiedHost : occupiedHosts) {
            hosts.add(String.format("%s:%s", occupiedHost, TRANSPORT_PORT));
        }
        return Joiner.on(",").join(hosts);
    }

    public TaskInfo taskInfo(Offer offer) {
        // docker image info
        ContainerInfo.DockerInfo dockerInfo = ContainerInfo.DockerInfo.newBuilder()
                .setImage(imageName)
                .setNetwork(ContainerInfo.DockerInfo.Network.HOST)
                .build();

        // container info
        ContainerInfo containerInfo = ContainerInfo.newBuilder()
                .setType(ContainerInfo.Type.DOCKER)
                .setDocker(dockerInfo)
                .build();

        // command info
        CommandInfo cmd = CommandInfo.newBuilder()
                .setShell(false)
                .addAllArguments(Arrays.asList(CMD,
                        String.format("-Des.cluster.name=%s", clusterName),
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


        // TODO: use configured resources instead of everything
        for (Protos.Resource resource : offer.getResourcesList()) {
            taskBuilder.addResources(resource);
        }

        return taskBuilder.build();
    }
}
