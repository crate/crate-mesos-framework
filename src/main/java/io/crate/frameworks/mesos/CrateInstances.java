package io.crate.frameworks.mesos;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        for (CrateInstance crateInstance : instances) {
            hosts.add(String.format("%s:%s", crateInstance.hostname(), crateInstance.transportPort()));
        }
        return Joiner.on(",").join(hosts);
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

    public CrateInstance byTaskId(final String taskId) {
        return Iterables.find(this, new Predicate<CrateInstance>() {
            @Override
            public boolean apply(CrateInstance input) {
                return input.taskId().equals(taskId);
            }
        });
    }
}
