package io.crate.frameworks.mesos.api;

public class ClusterInfo {

    private int instances;

    public ClusterInfo() {}

    public ClusterInfo(int instances) {
        this.instances = instances;
    }

    public int getInstances() {
        return instances;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

}
