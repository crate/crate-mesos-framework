package io.crate.frameworks.mesos;

import java.io.Serializable;

public class CrateInstance implements Serializable {

    private final String taskId;
    private final String hostname;
    private State state;

    public enum State implements Serializable {
        PENDING,
        RUNNING
    }

    public CrateInstance(String hostname, String taskId) {
        this.taskId = taskId;
        this.hostname = hostname;
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
}
