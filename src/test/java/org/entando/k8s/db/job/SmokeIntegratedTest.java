package org.entando.k8s.db.job;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(@Tag("end-to-end"))
class SmokeIntegratedTest {

    static final String ADMIN_USER = "postgres";
    static final String ADMIN_PASSWORD = "postgres";
    static final int PORT = 5432;
    static final int FORWARDED_LOCAL_PORT = 0;
    static final String DATABASE_NAME = "testdb";
    static final String MYSCHEMA = "myschema";
    static final String MYPASSWORD = "mypassword";
    private static final String NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("dbjob-ns");
    public static final String POSTGRES_POD_NAME = "pg-test";
    KubernetesClient client = new DefaultKubernetesClient();

    @AfterEach
    void cleanupNamespace() {
        client.pods().inNamespace(NAMESPACE).delete();
    }

    @BeforeEach
    void resetNamespace() {
        if (client.namespaces().withName(NAMESPACE).get() == null) {
            client.namespaces()
                    .create(new NamespaceBuilder().withNewMetadata().withName(NAMESPACE).endMetadata().build());
        } else {
            client.pods().inNamespace(NAMESPACE).delete();
            await().atMost(2, TimeUnit.MINUTES)
                    .until(() -> client.pods().inNamespace(NAMESPACE).list().getItems().isEmpty());
        }
    }

    @Test
    void testPostgresql() throws SQLException, InterruptedException {
        //Given I have a PG Database available on a given IP address
        String ip = preparePgDb();
        //When I run the DBSchemaJob image against that IP
        runDbSchemaJobAgainst(ip);
        //Then I can connect to the database using the resulting schema
        var portForward = forwardPort(POSTGRES_POD_NAME, PORT);
        verifyDatabaseState("localhost", portForward.getLocalPort());
    }

    private void verifyDatabaseState(String address, int port) throws SQLException {
        try (Connection connection = attemptConnection(address, port)) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    private LocalPortForward forwardPort(String podName, int remotePort) {
        return client.pods().inNamespace(NAMESPACE).withName(podName).portForward(remotePort, FORWARDED_LOCAL_PORT);
    }

    private Connection attemptConnection(String localAddress, int localPort) throws SQLException {
        String connectionUrl = String.format("jdbc:postgresql://%s:%d/%s", localAddress, localPort, DATABASE_NAME);
        await().atMost(1, TimeUnit.MINUTES).ignoreExceptions().until(() -> {
            DriverManager.getConnection(connectionUrl, MYSCHEMA, MYPASSWORD).close();
            return true;
        });
        return DriverManager.getConnection(connectionUrl, MYSCHEMA, MYPASSWORD);
    }

    private void runDbSchemaJobAgainst(String ip) throws InterruptedException {
        client.pods().inNamespace(NAMESPACE).create(new PodBuilder().withNewMetadata().withName("dbjob")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("dbjob-container")
                .withImage(resolveImage())
                .withEnv(new EnvVar("DATABASE_ADMIN_USER", ADMIN_USER, null),
                        new EnvVar("DATABASE_ADMIN_PASSWORD", ADMIN_PASSWORD, null),
                        new EnvVar("DATABASE_SERVER_HOST", ip, null),
                        new EnvVar("DATABASE_SERVER_PORT", String.valueOf(PORT), null),
                        new EnvVar("DATABASE_NAME", DATABASE_NAME, null),
                        new EnvVar("DATABASE_USER", MYSCHEMA, null),
                        new EnvVar("DATABASE_PASSWORD", MYPASSWORD, null),
                        new EnvVar("DATABASE_VENDOR", "postgresql", null)
                )
                .endContainer().endSpec().build());
        PodResource<Pod> job = client.pods().inNamespace(NAMESPACE).withName("dbjob");
        job.waitUntilCondition(pod -> pod.getStatus() != null && PodResult.of(pod).getState() == State.COMPLETED, 3,
                TimeUnit.MINUTES);
    }

    private String resolveImage() {
        return new EntandoImageResolver(null).determineImageUri("entando/entando-k8s-dbjob:"
                + EntandoOperatorTestConfig.getVersionOfImageUnderTest().orElse("0.0.0-SNAPSHOT-LOCAL-BUILD"));
    }

    private String preparePgDb() throws InterruptedException {
        client.pods().inNamespace(NAMESPACE).create(new PodBuilder().withNewMetadata().withName(POSTGRES_POD_NAME)
                .endMetadata()
                .withNewSpec().addNewContainer()
                .withName("pg-container")
                .withImage("centos/postgresql-96-centos7:latest")
                .withNewReadinessProbe()
                .withNewExec()
                .addToCommand("/bin/sh", "-i", "-c",
                        "psql -h 127.0.0.1 -U ${POSTGRESQL_USER} -q -d postgres -c '\\l'|grep ${POSTGRESQL_DATABASE}")
                .endExec()
                .endReadinessProbe()
                .withEnv(new EnvVar("POSTGRESQL_USER", "notused", null),
                        new EnvVar("POSTGRESQL_PASSWORD", "notused", null),
                        new EnvVar("POSTGRESQL_DATABASE", "testdb", null),
                        new EnvVar("POSTGRESQL_ADMIN_PASSWORD", "postgres", null))
                .endContainer().endSpec().build());
        PodResource<Pod> podResource = client.pods().inNamespace(NAMESPACE).withName(POSTGRES_POD_NAME);
        podResource.waitUntilCondition(pod -> pod.getStatus() != null && PodResult.of(pod).getState() == State.READY, 3,
                TimeUnit.MINUTES);
        return podResource.fromServer().get().getStatus().getPodIP();

    }
}
