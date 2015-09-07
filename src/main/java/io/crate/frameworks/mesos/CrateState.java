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

import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class CrateState implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrateState.class);

    private Observable<Integer> desiredInstances = new Observable<>(UNDEFINED_DESIRED_INSTANCES);
    private String frameworkId = null;
    private CrateInstances crateInstances = new CrateInstances();
    private HashMap<String, List<String>> excludedSlaves = new HashMap<>();
    private Set<String> slavesWithInstance = new HashSet<>();

    private int httpPort = 0;
    private int transportPort = 0;

    private static final long serialVersionUID = 1L;

    public static final int UNDEFINED_DESIRED_INSTANCES = -1;


    public static CrateState fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return new CrateState();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            return (CrateState) objectInputStream.readObject();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not deserialize ClusterState:", e);
        }
        return null;
    }

    public byte[] toStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(this);
        } catch (IOException e){
            LOGGER.error("Could not serialize ClusterState:", e);
        }
        return out.toByteArray();
    }

    public CrateInstances crateInstances() {
        return crateInstances;
    }

    public void instances(CrateInstances activeOrPendingInstances) {
        this.crateInstances = activeOrPendingInstances;
    }

    public Observable<Integer> desiredInstances() {
        return desiredInstances;
    }

    public void desiredInstances(int instances) {
        desiredInstances.setValue(instances);
    }

    public void frameworkId(String frameworkId) {
        this.frameworkId = frameworkId;
    }

    public Optional<String> frameworkId() {
        return Optional.fromNullable(frameworkId);
    }

    public void httpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int httpPort() {
        return this.httpPort;
    }

    public void transportPort(int transportPort) {
        this.transportPort = transportPort;
    }

    public int transportPort() {
        return this.transportPort;
    }

    public int missingInstances() {
        return desiredInstances().getValue() - crateInstances().size();
    }


    public boolean addSlaveIdToExcludeList(String reason, String slaveId) {
        if (!excludedSlaves.containsKey(reason)) {
            excludedSlaves.put(reason, new ArrayList<String>());
        }
        return excludedSlaves.get(reason).add(slaveId);
    }

    public void removeSlaveIdFromExcludeList(String slaveId) {
        for( Map.Entry<String, List<String>> slaves : excludedSlaves.entrySet()) {
          slaves.getValue().remove(slaveId);
        }
    }
    public boolean removeSlaveIdFromExcludeList(String reason, String slaveId) {
        if (!excludedSlaves.containsKey(reason)) {
            return false;
        }
        return excludedSlaves.get(reason).remove(slaveId);
    }

    public List<String> excludedSlaveIds(String reason){
        if (!excludedSlaves.containsKey(reason)) {
            return Collections.emptyList();
        }
        return excludedSlaves.get(reason);
    }

    public List<String> excludedSlaveIds() {
        if (excludedSlaves.size() == 0) return Collections.emptyList();
        List<String> allIds = new ArrayList<>();

        for( Map.Entry<String, List<String>> slaves : excludedSlaves.entrySet()) {
          allIds.addAll(slaves.getValue());
        }
        return allIds;

    }

    public Set<String> slavesWithInstances() {
        return slavesWithInstance;
    }

    public HashMap<String, List<String>> excludedSlaves () {
        return excludedSlaves;
    }

    @Override
    public String toString() {
        return String.format("%s { frameworkId=%s, crateInstances=%d, desiredInstances=%d }",
                this.getClass().getName(), frameworkId, crateInstances.size(), desiredInstances.getValue());
    }
}
