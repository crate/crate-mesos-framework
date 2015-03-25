package io.crate.frameworks.mesos.config;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.mesos.Protos;

import java.util.List;

public class Resources {

    public static boolean matches(List<Protos.Resource> offeredResources, Configuration configuration) {
        ImmutableMap<String, Protos.Resource> resourceMap = Maps.uniqueIndex(offeredResources, new Function<Protos.Resource, String>() {
            @Override
            public String apply(Protos.Resource input) {
                return input.getName();
            }
        });

        Protos.Resource cpus1 = resourceMap.get("cpus");
        if (cpus1.getScalar().getValue() < configuration.resCpus) {
            return false;
        }

        Protos.Resource mem = resourceMap.get("mem");

        //noinspection RedundantIfStatement
        if (mem.getScalar().getValue() < configuration.resMemory) {
            return false;
        }
        return true;

    }
}
