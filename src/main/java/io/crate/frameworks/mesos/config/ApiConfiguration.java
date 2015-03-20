package io.crate.frameworks.mesos.config;

import io.crate.frameworks.mesos.Env;

public class ApiConfiguration {

    private final int apiPort;

    public int apiPort() {
        return apiPort;
    }

    private static class EnvVariables {
        public static String API_PORT = "FRAMEWORK_API_PORT";
    }

    private static class Defaults {
        public static String API_PORT_DEFAULT = "4040";
    }

    public ApiConfiguration(int apiPort) {
        this.apiPort = apiPort;
    }

    public static ApiConfiguration fromEnvironment() {
        return new ApiConfiguration(Integer.parseInt(Env.option(EnvVariables.API_PORT).or(Defaults.API_PORT_DEFAULT)));
    }

}
