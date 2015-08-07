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

package io.crate.frameworks.mesos.config;

import com.google.common.base.Function;
import com.google.common.collect.*;
import org.apache.mesos.Protos;

import java.util.List;

import static com.google.common.collect.Iterables.getOnlyElement;

public class Resources {

    private static final Function<Protos.Resource, String> RESOURCE_NAME = new Function<Protos.Resource, String>() {
        @Override
        public String apply (Protos.Resource input) {
            return input != null ? input.getName() : "Unnamed";
        }
    };

    @SuppressWarnings("RedundantIfStatement")
    public static boolean matches(List<Protos.Resource> offeredResources, Configuration configuration) {
        ImmutableListMultimap<String, Protos.Resource> resourceMap = Multimaps.index(offeredResources, RESOURCE_NAME);

        Protos.Resource cpus1 = getOnlyElement(resourceMap.get("cpus"));
        if (cpus1.getScalar().getValue() < configuration.resCpus) {
            return false;
        }

        Protos.Resource mem = getOnlyElement(resourceMap.get("mem"));

        if (mem.getScalar().getValue() < configuration.resMemory) {
            return false;
        }

        ImmutableList<Protos.Resource> ports = resourceMap.get("ports");
        if(!isPortInRange(configuration.httpPort, ports)) {
            return false;
        }
        if(!isPortInRange(configuration.transportPort, ports)) {
            return false;
        }

        return true;
    }

    private static boolean isPortInRange(int port, List<Protos.Resource> portResources) {
        for (Protos.Resource portResource : portResources) {
            for (final Protos.Value.Range range : portResource.getRanges().getRangeList()) {
                final long begin = range.getBegin();
                final long end = range.getEnd();
                if(port >= begin && port <= end) {
                    return true;
                }
            }
        }
        return false;
    }
}
