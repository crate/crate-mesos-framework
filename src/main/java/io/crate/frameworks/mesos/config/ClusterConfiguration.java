package io.crate.frameworks.mesos.config;


import io.crate.frameworks.mesos.Env;

public class ClusterConfiguration {

    private final String clusterName;

    private final int nodeCount;

    public String clusterName() {
        return clusterName;
    }

    public int nodeCount() {
        return nodeCount;
    }

    private static class EnvVariables {

        public static String CRATE_CLUSTER_NAME = "CRATE_CLUSTER_NAME";
        public static String CRATE_NODE_COUNT = "CRATE_NODE_COUNT";
    }

    private static class Defaults {

        public static String CRATE_CLUSTER_NAME_DEFAULT = "crate";
        public static String CRATE_NODE_COUNT_DEFAULT = "1";
    }

    public static ClusterConfiguration fromEnvironment() {
        return new ClusterConfiguration(
                Env.option(EnvVariables.CRATE_CLUSTER_NAME).or(Defaults.CRATE_CLUSTER_NAME_DEFAULT),
                Integer.parseInt(Env.option(EnvVariables.CRATE_NODE_COUNT).or(Defaults.CRATE_NODE_COUNT_DEFAULT))
        );
    }

    public ClusterConfiguration(String clusterName, int nodeCount) {
        this.clusterName = clusterName;
        this.nodeCount = nodeCount;
    }

}
