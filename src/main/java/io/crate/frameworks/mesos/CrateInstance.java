package io.crate.frameworks.mesos;

import java.io.Serializable;

public class CrateInstance implements Serializable {

    private final String taskId;
    private final String hostname;
    private final String version;
    private final int transportPort;
    private State state;

    public enum State implements Serializable {
        PENDING,
        RUNNING
    }

    public CrateInstance(String hostname, String taskId, String version, int transportPort) {
        this.taskId = taskId;
        this.hostname = hostname;
        this.version = version;
        this.transportPort = transportPort;
        state = State.PENDING;
    }

    public String taskId() {
        return taskId;
    }

    public State state() {
        return state;
    }

    public void state(State newState) {
        this.state = newState;
    }

    public String hostname() {
        return hostname;
    }

    public String version() {
        return version;
    }

    public int transportPort() {
        return transportPort;
    }

    @Override
    public String toString() {
        return "CrateInstance{" +
                "taskId='" + taskId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", version='" + version + '\'' +
                ", transportPort=" + transportPort +
                ", state=" + state +
                '}';
    }
}
