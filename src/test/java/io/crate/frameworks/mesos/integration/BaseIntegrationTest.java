package io.crate.frameworks.mesos.integration;

import com.containersol.minimesos.cluster.MesosAgent;
import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.junit.MesosClusterTestRule;
import com.jayway.awaitility.Awaitility;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.json.JSONArray;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.TestCase.fail;

public class BaseIntegrationTest {

    private static final String MESOS_JAR_PATTERN = "crate-mesos-(.*?).jar";
    private static final int HTTP_PORT = 4200;
    private static final int TRANSPORT_PORT = 4300;
    private static final int API_PORT = 4242;
    private static final int WAIT_TIMEOUT_SECONDS = 90;

    @ClassRule
    public static MesosClusterTestRule testRule =
            MesosClusterTestRule.fromFile("src/test/resources/minimesosConf");

    public static MesosCluster cluster = testRule.getMesosCluster();

    private static String crateMesosFrameworkHostIp;

    @BeforeClass
    public static void beforeClass() throws IOException {
        cluster.getMarathon().deployApp(appMarathonJson());
        waitForCrateFramework();
    }

    private static String appMarathonJson() throws IOException {
        File taskFile = new File("src/test/resources/app.json");
        if (!taskFile.exists()) {
            fail("Failed to find task info file " + taskFile.getAbsolutePath());
        }

        try (FileInputStream fis = new FileInputStream(taskFile)) {
            String appJson = IOUtils.toString(fis);
            String mesosFrameworkPath = mesosCrateFrameworkJarPath();
            appJson = replaceToken(appJson, "HTTP_PORT", String.valueOf(HTTP_PORT));
            appJson = replaceToken(appJson, "TRANSPORT_PORT", String.valueOf(TRANSPORT_PORT));
            appJson = replaceToken(appJson, "API_PORT", String.valueOf(API_PORT));
            appJson = replaceToken(appJson, "CRATE_MESOS_FRAMEWORK_PATH", mesosFrameworkPath);
            appJson = replaceToken(appJson, "CRATE_MESOS_FRAMEWORK",
                    Paths.get(mesosFrameworkPath).getFileName().toString());
            appJson = replaceToken(appJson, "ZOOKEEPER", cluster.getZooKeeper().getIpAddress());
            appJson = replaceToken(appJson, "MESOS_MASTER", cluster.getZooKeeper().getFormattedZKAddress());
            appJson = replaceToken(appJson, "CRATE_VERSION", crateVersion());
            return appJson;
        }
    }

    private static String replaceToken(String input, String token, String value) {
        String tokenRegex = String.format("\\$\\{%s\\}", token);
        return input.replaceAll(tokenRegex, value);
    }

    private static String crateVersion() {
        String cp = System.getProperty("java.class.path");
        Matcher m = Pattern.compile("crate-client-([\\d\\.]{5,})\\.jar").matcher(cp);
        if (m.find()) {
            return m.group(1);
        }
        throw new RuntimeException("Cannot the crate version");
    }

    private static String mesosCrateFrameworkJarPath() {
        File directory = Paths.get(System.getProperty("user.dir"), "build/libs").toFile();
        FileFilter filter = new RegexFileFilter(MESOS_JAR_PATTERN);
        File[] files = directory.listFiles(filter);
        assert files.length == 1;
        return files[0].getAbsolutePath();
    }

    private static void waitForCrateFramework() {
        Awaitility.await().atMost(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for (MesosAgent mesosAgent : cluster.getAgents()) {
                    if (!mesosAgent.getState().getFrameworks().isEmpty()) {
                        try {
                            int status = Unirest.head(
                                    String.format("http://%s:%d/cluster", mesosAgent.getIpAddress(), API_PORT)
                            ).asJson().getStatus();
                            crateMesosFrameworkHostIp = mesosAgent.getIpAddress();
                            return status == 200;
                        } catch (UnirestException e) {
                            //ignore
                        }
                    }
                }
                return false;
            }
        });
    }

    public void shutdown() {
        Awaitility.await().atMost(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    Unirest.post(
                            String.format("http://%s:%d/cluster/shutdown", crateMesosFrameworkHostIp, API_PORT)
                    ).asJson();
                } catch (UnirestException e) {
                    //ignore
                }
                return 0 == crateNodesCount();
            }
        });
    }

    public void scaleCrate(final int numNodes) {
        try {
            Unirest.post(String.format("http://%s:%d/cluster/resize", crateMesosFrameworkHostIp, API_PORT))
                    .header("Content-Type", "application/json")
                    .body(String.format("{\"instances\": %d}", numNodes)).asJson();
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Error in scaling crate.");
        }

        Awaitility.await().atMost(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return numNodes == crateNodesCount();
            }
        });
    }

    public int crateNodesCount() throws UnirestException {
        List<String> crateHosts = crateActiveHosts();
        if (crateHosts.isEmpty()) {
            return 0;
        }
        JSONArray rows = Unirest.post(String.format("http://%s:%d/_sql", crateHosts.get(0), HTTP_PORT))
                .header("Content-Type", "application/json")
                .body("{\"stmt\": \"select count(*) from sys.nodes\"}")
                .asJson().getBody().getObject().getJSONArray("rows");
        return rows.getJSONArray(0).getInt(0);
    }

    private List<String> crateActiveHosts() {
        List<String> hosts = new ArrayList<>();
        for (MesosAgent agent : cluster.getAgents()) {
            try {
                String crateUrl = String.format("http://%s:%d", agent.getIpAddress(), HTTP_PORT);
                int status = Unirest.head(crateUrl).asJson().getStatus();
                if (status == 200) {
                    hosts.add(agent.getIpAddress());
                }
            } catch (UnirestException e) {
                //ignore
            }
        }
        return hosts;
    }

    public HttpResponse<JsonNode> execute(String stmt) throws UnirestException {
        List<String> crateHosts = crateActiveHosts();
        if (crateHosts.isEmpty()) {
            throw new RuntimeException("Crate nodes are not running");
        }
        return Unirest.post(String.format("http://%s:%d/_sql", crateHosts.get(0), HTTP_PORT))
                .header("Content-Type", "application/json")
                .body(String.format("{\"stmt\": \"%s\"}", stmt))
                .asJson();
    }

}
