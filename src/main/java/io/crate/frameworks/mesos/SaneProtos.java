package io.crate.frameworks.mesos;

import org.apache.mesos.Protos;

public class SaneProtos {


    public static Protos.Resource cpus(double value) {
        return scalarResource("cpus", value, null);
    }

    public static Protos.Resource mem(double value) {
        return scalarResource("mem", value, null);
    }

    public static Protos.TaskID taskID(String taskId) {
        return Protos.TaskID.newBuilder().setValue(taskId).build();
    }

    public static Protos.Resource scalarResource(String name, double value, String role) {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder()
                .setName(name)
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(value).build());

        if (role != null) {
            builder.setRole(role);
        }
        return builder.build();
    }
}
