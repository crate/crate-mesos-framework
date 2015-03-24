package io.crate.frameworks.mesos.config;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TreeSet;

import static com.google.common.collect.Sets.newTreeSet;

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

        Protos.Resource ports = resourceMap.get("ports");

        //noinspection RedundantIfStatement
        if(!checkPorts(configuration, ports)) {
            return false;
        }


        return true;

    }

    private static boolean checkPorts(Configuration configuration, Protos.Resource ports) {
        boolean portsMatch = false;
        for (final Protos.Value.Range range : ports.getRanges().getRangeList()) {
            final long begin = range.getBegin();
            final long end = range.getEnd();
            if(configuration.httpPort().longValue() > begin && configuration.httpPort().longValue() < end) {
                portsMatch = true;
            }
        }
        return portsMatch;
    }
}
