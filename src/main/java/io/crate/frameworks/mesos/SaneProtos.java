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

import org.apache.mesos.Protos;

public class SaneProtos {

    public static Protos.Resource cpus(double value) {
        return scalarResource("cpus", value);
    }

    public static Protos.Resource mem(double value) {
        return scalarResource("mem", value);
    }

    public static Protos.TaskID taskID(String taskId) {
        return Protos.TaskID.newBuilder().setValue(taskId).build();
    }

    public static Protos.Resource scalarResource(String name, double value) {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder()
                .setName(name)
                .setRole("*")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(value).build());
        return builder.build();
    }


    public static Protos.Resource ports(int from, int to) {
        return Protos.Resource.newBuilder()
                .setName("ports")
                .setRole("*")
                .setType(Protos.Value.Type.RANGES)
                .setRanges(Protos.Value.Ranges.newBuilder().addRange(
                        Protos.Value.Range.newBuilder().setBegin(from).setEnd(to).build()))
                .build();
    }
}
