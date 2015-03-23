package io.crate.frameworks.mesos.config;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import io.crate.frameworks.mesos.CrateContainer;
import io.crate.frameworks.mesos.SaneProtos;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.regex.Pattern;

public class Configuration {

    @Parameter(names = { "--crate-version" }, required = true, validateWith = VersionValidator.class)
    String version;

    @Parameter(names = { "--crate-cluster-name" })
    String clusterName = "crate";

    @Parameter(names = { "--crate-node-count" })
    Integer nodeCount = 0;

    @Parameter(names = { "--crate-http-port" })
    Integer httpPort = 4200;

    @Parameter(names = { "--api-port" })
    Integer apiPort = 4040;

    @Parameter(names = { "--resource-cpus" })
    Double resCpus = 0.5d;

    @Parameter(names = { "--resource-memory" })
    Double resMemory = 512d;

    @Parameter(names = { "--resource-heap" })
    Double resHeap = 256d;

    @Parameter(names = { "--resource-disk" })
    Double resDisk = 1024d;


    public String version() {
        return version;
    }

    public String clusterName() {
        return clusterName;
    }

    public Integer nodeCount() {
        return nodeCount;
    }

    public Integer httpPort() {
        return httpPort;
    }

    public Double resourcesCpus() {
        return resCpus;
    }

    public Double resourcesMemory() {
        return resMemory;
    }

    public Double resourcesHeap() {
        return resHeap;
    }

    public Double resourcesDisk() {
        return resDisk;
    }

    public Integer apiPort() {
        return apiPort;
    }

    public Iterable<? extends Protos.Resource> getAllRequiredResources() {
        return Arrays.asList(
                SaneProtos.cpus(resCpus),
                SaneProtos.mem(resMemory),
                SaneProtos.ports(httpPort, httpPort, "*"),
                SaneProtos.ports(CrateContainer.TRANSPORT_PORT, CrateContainer.TRANSPORT_PORT, "*")
        );
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "version='" + version + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", nodeCount=" + nodeCount +
                ", apiPort=" + apiPort +
                ", resCpus=" + resCpus +
                ", resMemory=" + resMemory +
                ", resHeap=" + resHeap +
                ", resDisk=" + resDisk +
                '}';
    }

    public void version(String version) {
        this.version = version;
    }

    public static class VersionValidator implements IParameterValidator {

        private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

        @Override
        public void validate(String name, String value) throws ParameterException {
            if (!VERSION_PATTERN.matcher(value).matches()) {
                throw new ParameterException(
                        String.format("The specified CRATE_VERSION \"%s\" isn't a valid version", value));
            }
        }
    }
}
