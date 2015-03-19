package io.crate.frameworks.mesos;


import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.List;

public class ResourceConfiguration {

    private static class EnvVariables {

        public final static String CPU_CORES = "CRATE_RESOURCE_CPU_CORES";
        public final static String MEMORY = "CRATE_RESOURCE_MEMORY";
        public final static String HEAP = "CRATE_RESOURCE_HEAP";
        public final static String DISK = "CRATE_RESOURCE_DISK";
    }

    public static class Defaults {
        public final static String CPUS = "0.5";
        public final static String MEMORY = "100";
        public final static String HEAP = "50";
        public final static String DISK = "1024";
    }

    private final double cpus;
    private final double memory;
    private final double heap;
    private final double disk;

    public static ResourceConfiguration fromEnvironment() {
        return new ResourceConfiguration(
                Double.parseDouble(Env.option(EnvVariables.CPU_CORES).or(Defaults.CPUS)),
                Double.parseDouble(Env.option(EnvVariables.MEMORY).or(Defaults.MEMORY)),
                Double.parseDouble(Env.option(EnvVariables.HEAP).or(Defaults.HEAP)),
                Double.parseDouble(Env.option(EnvVariables.DISK).or(Defaults.DISK))
        );
    }

    public ResourceConfiguration(double cpus,
                                 double memory,
                                 double heap,
                                 double disk) {
        this.cpus = cpus;
        this.memory = memory;
        this.heap = heap;
        this.disk = disk;
    }

    public boolean matches(List<Protos.Resource> resourcesList) {
        ImmutableMap<String, Protos.Resource> resourceMap = Maps.uniqueIndex(resourcesList, new Function<Protos.Resource, String>() {
            @Override
            public String apply(Protos.Resource input) {
                return input.getName();
            }
        });

        Protos.Resource cpus1 = resourceMap.get("cpus");
        if (cpus1.getScalar().getValue() < cpus) {
            return false;
        }

        Protos.Resource mem = resourceMap.get("mem");

        //noinspection RedundantIfStatement
        if (mem.getScalar().getValue() < memory) {
            return false;
        }
        return true;
    }

    public Protos.Resource cpus() {
        return SaneProtos.cpus(cpus);
    }

    public Protos.Resource memory() {
        return SaneProtos.mem(memory);
    }


    public Iterable<? extends Protos.Resource> getAllRequiredResources() {
        return Arrays.asList(
                cpus(),
                memory()
                // TODO: port requirement: taskBuilder.addResources(ports(4200, 4200, "crate"));
        );
    }
}
