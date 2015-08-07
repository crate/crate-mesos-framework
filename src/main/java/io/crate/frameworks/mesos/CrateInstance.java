/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.frameworks.mesos;

import java.io.Serializable;

public class CrateInstance implements Serializable {

    private final String taskId;
    private final String hostname;
    private final String version;
    private final int transportPort;
    private final String executorID;
    private final String slaveID;
    private String nodeId;  // todo:  this is never read
    private State state;

    public enum State implements Serializable {
        PENDING,
        RUNNING
    }

    public CrateInstance(String hostname, String taskId, String version, int transportPort,
                         String executorID, String slaveID) {
        this.taskId = taskId;
        this.hostname = hostname;
        this.version = version;
        this.transportPort = transportPort;
        this.executorID = executorID;
        this.slaveID = slaveID;
        nodeId = null;
        state = State.PENDING;
    }

    public String taskId() {
        return taskId;
    }

    public void nodeId(String nodeId) {
        this.nodeId = nodeId;
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

    public String executorID() { return executorID; }

    public String slaveID() { return slaveID; }

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
