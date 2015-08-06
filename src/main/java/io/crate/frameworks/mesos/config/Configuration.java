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

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import io.crate.frameworks.mesos.SaneProtos;
import org.apache.mesos.Protos;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Configuration implements Serializable {

    @Parameter(names = { "--framework-user" })
    public String user = "crate";

    @Parameter(names = { "--framework-role" })
    public String role = "*";

    @Parameter(names = { "--framework-name" })
    public String frameworkName = "crate-mesos";

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

    @Parameter(names = { "--crate-data-path" })
    public String dataPath = null;

    @Parameter(names = { "--crate-blob-path" })
    public String blobPath = null;

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

    private List<String> crateArgs = ImmutableList.of();

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
                SaneProtos.ports(httpPort, httpPort),
                SaneProtos.ports(transportPort, transportPort)
        );
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "mesosMaster='" + mesosMaster + '\'' +
                ", zookeeper='" + zookeeper + '\'' +
                ", version='" + version + '\'' +
                ", frameworkName='" + frameworkName + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", nodeCount=" + nodeCount +
                ", httpPort=" + httpPort +
                ", transportPort=" + transportPort +
                ", dataPath="+ dataPath +
                ", blobPath="+ blobPath +
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

    public boolean versionIsDownloadURL() {
        return (this.version != null && this.version.startsWith("http"));
    }

    public void crateArgs(List<String> crateArgs) {
        this.crateArgs = crateArgs;
    }

    public List<String> crateArgs() {
        return crateArgs;
    }

    public static class VersionValidator implements IParameterValidator {

        private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
        private static final Pattern URL_VERSION_PATTERN = Pattern.compile("^https?:\\/\\/.*crate-\\d+\\.\\d+\\.\\d+(.*)\\.tar\\.gz");

        @Override
        public void validate(String name, String value) throws ParameterException {
            Matcher matcher;
            if (value.startsWith("http")) {
                matcher = URL_VERSION_PATTERN.matcher(value);
            } else {
                matcher = VERSION_PATTERN.matcher(value);
            }
            if (!matcher.matches()) {
                throw new ParameterException(
                        String.format("The specified Crate version \"%s\" isn't a valid version or download location.", value));
            }
        }
    }
}
