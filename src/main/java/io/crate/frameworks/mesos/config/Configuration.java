package io.crate.frameworks.mesos.config;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import io.crate.frameworks.mesos.SaneProtos;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.regex.Pattern;

public class Configuration {

    @Parameter(names = { "--mesos-master" })
    private String mesosMaster = null;

    @Parameter(names = { "--zookeeper" })
    public String zookeeper = "localhost:2181";

    @Parameter(names = { "--crate-version" }, required = true, validateWith = VersionValidator.class)
    public String version;

    @Parameter(names = { "--crate-cluster-name" })
    public String clusterName = "crate";

    @Parameter(names = { "--crate-node-count" })
    public Integer nodeCount = 0;

    @Parameter(names = { "--crate-http-port" })
    public Integer httpPort = 4200;

    @Parameter(names = { "--crate-transport-port" })
    public Integer transportPort = 4300;

    @Parameter(names = { "--api-port" })
    public Integer apiPort = 4040;

    @Parameter(names = { "--resource-cpus" })
    public Double resCpus = 0.5d;

    @Parameter(names = { "--resource-memory" })
    public Double resMemory = 512d;

    @Parameter(names = { "--resource-heap" })
    public Double resHeap = 256d;

    @Parameter(names = { "--resource-disk" })
    public Double resDisk = 1024d;

    public String mesosMaster() {
        if (mesosMaster == null) {
            return String.format("zk://%s/mesos", zookeeper);
        }
        return mesosMaster;
    }

    public Iterable<? extends Protos.Resource> getAllRequiredResources() {
        return Arrays.asList(
                SaneProtos.cpus(resCpus),
                SaneProtos.mem(resMemory),
                SaneProtos.ports(httpPort, httpPort, "*"),
                SaneProtos.ports(transportPort, transportPort, "*")
        );
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "mesosMaster='" + mesosMaster + '\'' +
                ", zookeeper='" + zookeeper + '\'' +
                ", version='" + version + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", nodeCount=" + nodeCount +
                ", httpPort=" + httpPort +
                ", transportPort=" + transportPort +
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
