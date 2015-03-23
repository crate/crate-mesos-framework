package io.crate.frameworks.mesos;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.io.Serializable;
import java.util.*;

public class CrateInstances implements Serializable, Iterable<CrateInstance> {

    private final ArrayList<CrateInstance> instances = new ArrayList<>();
    private HashSet<String> hosts;

    public CrateInstances() {
    }

    public int size() {
        return instances.size();
    }

    public boolean anyOnHost(final String hostname) {
        return hosts().contains(hostname);
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

    public void setToRunning(String taskId) {
        for (CrateInstance instance : instances) {
            if (instance.taskId().equals(taskId)) {
                instance.state(CrateInstance.State.RUNNING);
            }
        }
    }

    public void removeTask(String taskId) {
        List<Integer> toRemove = new ArrayList<>();
        for (int i = 0; i < instances.size(); i++) {
            CrateInstance crateInstance = instances.get(i);
            if (crateInstance.taskId().equals(taskId)) {
                toRemove.add(i);
                if (hosts != null) {
                    hosts.remove(crateInstance.hostname());
                }
            }
        }
        for (Integer i : toRemove) {
            instances.remove((int) i);
        }
    }

    @Override
    public Iterator<CrateInstance> iterator() {
        return instances.iterator();
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
