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

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.io.Serializable;
import java.util.*;

public class CrateInstances implements Serializable, Iterable<CrateInstance> {

    private final ArrayList<CrateInstance> instances = new ArrayList<>();
    private HashSet<String> hosts;

    public int size() {
        return instances.size();
    }

    public boolean anyOnHost(final String hostname) {
        return hosts().contains(hostname);
    }

    public String unicastHosts() {
        List<String> hosts = new ArrayList<>(instances.size());
        for (CrateInstance instance : instances) {
            hosts.add(instance.connectionString());
        }
        return Joiner.on(",").join(hosts);
    }

    public String connectionString() {
        List<String> hosts = new ArrayList<>();
        for (CrateInstance instance : runningInstances()) {
            hosts.add(instance.connectionString());
        }
        return Joiner.on(",").join(hosts);
    }

    public static int calculateQuorum(int expectedNodes) {
        return (int) Math.ceil((expectedNodes + 1.0f) / 2.0f);
    }

    public Set<CrateInstance> runningInstances() {
        HashSet<CrateInstance> running = new HashSet<>();
        for (CrateInstance instance : instances) {
            if (instance.state() == CrateInstance.State.RUNNING) {
                running.add(instance);
            }
        }
        return running;
    }

    public Set<String> hosts() {
        // TODO: probably just expose hosts as iterable and avoid the internal set...
        if (hosts == null) {
            hosts = new HashSet<>(instances.size());
            for (CrateInstance instance : instances) {
                hosts.add(instance.hostname());
            }
        }
        return hosts;
    }

    public void addInstance(CrateInstance crateInstance) {
        instances.add(crateInstance);
        if (hosts != null) {
            hosts.add(crateInstance.hostname());
        }
    }

    public void setToRunning(String taskId, String nodeId) {
        for (CrateInstance instance : instances) {
            if (instance.taskId().equals(taskId)) {
                instance.state(CrateInstance.State.RUNNING);
                instance.nodeId(nodeId);
            }
        }
    }

    public void removeTask(String taskId) {
        for (int i = instances.size() -1; i >= 0; i--) {
            CrateInstance crateInstance = instances.get(i);
            if (crateInstance.taskId().equals(taskId)) {
                instances.remove(i);
                if (hosts != null) {
                    hosts.remove(crateInstance.hostname());
                }
            }
        }
    }

    @Override
    public Iterator<CrateInstance> iterator() {
        return instances.iterator();
    }

    public CrateInstance get(int index) {
        return instances.get(index);
    }

    public CrateInstance byTaskId(final String taskId) {
        return Iterables.find(this, new Predicate<CrateInstance>() {
            @Override
            public boolean apply(CrateInstance input) {
                return input.taskId().equals(taskId);
            }
        });
    }
}
