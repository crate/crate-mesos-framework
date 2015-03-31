package io.crate.frameworks.mesos;

import com.google.common.base.Optional;
import org.apache.mesos.Protos;
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

    private static final long serialVersionUID = 1L;

    public static final int UNDEFINED_DESIRED_INSTANCES = -1;


    public static CrateState fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return new CrateState();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            CrateState state = (CrateState) objectInputStream.readObject();
            return state;
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
        for (String key : excludedSlaves.keySet()) {
            excludedSlaves.get(key).remove(slaveId);
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
        for (String key : excludedSlaves.keySet()) {
            allIds.addAll(excludedSlaves.get(key));
        }
        return allIds;

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
