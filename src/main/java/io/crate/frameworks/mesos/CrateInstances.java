package io.crate.frameworks.mesos;

import java.io.*;
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

    public static CrateInstances fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return new CrateInstances();
        }

        // TODO: clean up exception handling
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            CrateInstances crateInstances = (CrateInstances) objectInputStream.readObject();
            crateInstances.hosts = null;
            return crateInstances;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] toStream() {
        // TODO: clean up exception handling

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(this);
        } catch (IOException e){

        }
        return out.toByteArray();
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
}
