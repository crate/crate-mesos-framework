package io.crate.frameworks.mesos.api;

/**
 * A model that represents a cluster resize action.
 */
public class ClusterResizeRequest {

    private int instances;

    public ClusterResizeRequest() {}

    public ClusterResizeRequest(int instances) {
        this.instances = instances;
    }

    public int getInstances() {
        return instances;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

}
