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
            return input.getName();
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
