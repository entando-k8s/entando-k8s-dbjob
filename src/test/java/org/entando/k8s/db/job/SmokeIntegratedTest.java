package org.entando.k8s.db.job;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.controller.EntandoImageResolver;
import org.entando.kubernetes.controller.integrationtest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(@Tag("end-to-end"))
class SmokeIntegratedTest {

    static final String ADMIN_USER = "postgres";
    static final String ADMIN_PASSWORD = "postgres";
    static final String PORT = "5432";
    static final String DATABASE_NAME = "testdb";
    static final String MYSCHEMA = "myschema";
    static final String MYPASSWORD = "mypassword";
    private static final String NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("dbjob-ns");
    static final String LOCAL_SERVICE_NAME = NAMESPACE + "-pg-service";
    KubernetesClient client = new DefaultKubernetesClient();

    @BeforeEach
    void cleanNamespace() {
        if (client.namespaces().withName(NAMESPACE).get() == null) {
            client.namespaces().createNew().withNewMetadata().withName(NAMESPACE).endMetadata().done();
        } else {
            client.pods().inNamespace(NAMESPACE).delete();
            await().atMost(2, TimeUnit.MINUTES).until(() -> client.pods().inNamespace(NAMESPACE).list().getItems().isEmpty());
        }
        cleanupLocalResources();
    }

    private void cleanupLocalResources() {
        force(() -> client.services().inNamespace(client.getNamespace()).withName(LOCAL_SERVICE_NAME).delete());
        force(() -> client.endpoints().inNamespace(client.getNamespace()).withName(LOCAL_SERVICE_NAME).delete());
    }

    private void force(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            Logger.getAnonymousLogger().log(Level.WARNING, "Operation failed.", e);
        }

    }

    @Test
    void testPostgresql() throws SQLException {
        //Given I have a PG Database available on a given IP address
        String ip = preparePgDb();
        //When I run the DBSchemaJob image against that IP
        runDbSchemaJobAgainst(ip);
        //Then I can connect to the database using the resulting schema
        Service service = createDelegatingServiceTo(ip);
        verifyDatabaseState(service);
        cleanupLocalResources();//Only on success
    }

    private void verifyDatabaseState(Service service) throws SQLException {
        try (Connection connection = attemptConnection(service)) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    private Service createDelegatingServiceTo(String ip) {
        Service service = client.services()
                .create(new ServiceBuilder().withNewMetadata().withName(LOCAL_SERVICE_NAME).withNamespace(client.getNamespace())
                        .endMetadata()
                        .withNewSpec()
                        .addNewPort()
                        .withPort(5432)
                        .withNewTargetPort(5432)
                        .endPort()
                        .endSpec()
                        .build());
        client.endpoints().create(new EndpointsBuilder().withNewMetadata().withName(LOCAL_SERVICE_NAME).withNamespace(client.getNamespace())
                .endMetadata()
                .addNewSubset()
                .addNewAddress().withIp(ip)
                .endAddress()
                .addNewPort()
                .withPort(5432)
                .endPort()
                .endSubset()
                .build());
        return service;
    }

    private Connection attemptConnection(Service service) throws SQLException {
        await().atMost(1, TimeUnit.MINUTES).ignoreExceptions().until(() -> {
            DriverManager
                    .getConnection("jdbc:postgresql://" + service.getSpec().getClusterIP() + ":5432/" + DATABASE_NAME, MYSCHEMA, MYPASSWORD)
                    .close();
            return true;
        });
        return DriverManager
                .getConnection("jdbc:postgresql://" + service.getSpec().getClusterIP() + ":5432/" + DATABASE_NAME, MYSCHEMA, MYPASSWORD);
    }

    private void runDbSchemaJobAgainst(String ip) {
        client.pods().inNamespace(NAMESPACE).createNew().withNewMetadata().withName("dbjob")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("dbjob-container")
                .withImage(resolveImage())
                .withEnv(new EnvVar("DATABASE_ADMIN_USER", ADMIN_USER, null),
                        new EnvVar("DATABASE_ADMIN_PASSWORD", ADMIN_PASSWORD, null),
                        new EnvVar("DATABASE_SERVER_HOST", ip, null),
                        new EnvVar("DATABASE_SERVER_PORT", PORT, null),
                        new EnvVar("DATABASE_NAME", DATABASE_NAME, null),
                        new EnvVar("DATABASE_USER", MYSCHEMA, null),
                        new EnvVar("DATABASE_PASSWORD", MYPASSWORD, null),
                        new EnvVar("DATABASE_VENDOR", "postgresql", null)
                )
                .endContainer().endSpec().done();
        PodResource<Pod, DoneablePod> job = client.pods().inNamespace(NAMESPACE).withName("dbjob");
        new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(60)).throwException(RuntimeException.class)
                .waitOn(job);
    }

    private String resolveImage() {
        return new EntandoImageResolver(null).determineImageUri("entando/entando-k8s-dbjob",
                EntandoOperatorTestConfig.getVersionOfImageUnderTest());
    }

    private String preparePgDb() {
        client.pods().inNamespace(NAMESPACE).createNew().withNewMetadata().withName("pg-test")
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
                .endContainer().endSpec().done();
        PodResource<Pod, DoneablePod> podResource = client.pods().inNamespace(NAMESPACE).withName("pg-test");
        new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(60)).throwException(RuntimeException.class)
                .waitOn(podResource);
        return podResource.fromServer().get().getStatus().getPodIP();

    }
}
