package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGConnectionPoolDataSource;

@TestMethodOrder(OrderAnnotation.class)
@Tags(@Tag("integration"))
class CreatePostgresqlSchemaTest {

    @Order(1)
    @Test
    void waitForDbms() {
        Map<String, String> props = getBaseProperties();
        var databaseAdminConfig = new PropertiesBasedDatabaseAdminConfig(props);
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        await().atMost(30, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
            try (Connection connection = dialect.connectAsAdmin(databaseAdminConfig)) {
                connection.createStatement();
                return true;
            }
        });
    }

    @Test
    void testSimpleCreate() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.execute();
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = DriverManager
                .getConnection("jdbc:postgresql://" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    private void testCreate(Map<String, String> props, String password) throws SQLException {
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.execute();
        try (Connection connection = DriverManager
                .getConnection("jdbc:postgresql://" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        password)) {
            assertTrue(connection.prepareStatement("SELECT 1").executeQuery().next());
        }
    }

    @Test
    void testDatasource() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.execute();
        PGConnectionPoolDataSource ds = new PGConnectionPoolDataSource();
        ds.setServerName(getDatabaseServerHost());
        ds.setDatabaseName(getDatabaseName());
        ds.setPortNumber(Integer.valueOf(getPort()));
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = ds.getConnection("myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    private String getDatabaseName() {
        return TestConfigProperty.POSTGRESQL_DATABASE_NAME.resolve();
    }

    private Map<String, String> getBaseProperties() {
        //NB! These correspond to the ENV vars in docker-compose-cicd.yml
        Map<String, String> props = new HashMap<>();
        props.put("DATABASE_ADMIN_USER", TestConfigProperty.POSTGRESQL_ADMIN_USER.resolve());
        props.put("DATABASE_ADMIN_PASSWORD", TestConfigProperty.POSTGRESQL_ADMIN_PASSWORD.resolve());
        props.put("DATABASE_SERVER_HOST", getDatabaseServerHost());
        props.put("DATABASE_SERVER_PORT", "" + getPort() + "");
        props.put("DATABASE_VENDOR", "postgresql");
        props.put("DATABASE_NAME", getDatabaseName());
        return props;
    }

    private String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_POSTGRESQL_SERVICE_HOST")).orElse("localhost");
    }

    private String getPort() {
        return ofNullable(System.getenv("EXTERNAL_POSTGRESQL_SERVICE_PORT")).orElse("5432");
    }

    @Test
    void testIdempotent() throws Exception {
        //Given a user/schema combination that does not exist
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //But is then created
        testCreate(props, "test123");
        //Expect a second attempt to create it not to fail, even though it already exists
        testCreate(props, "test123");
    }

    @Test
    void testForcePasswordReset() throws Exception {
        //Given that a specific user/schema combination does not exist
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //But then it is created
        testCreate(props, "test123");
        //And we enabled password resets
        props.put("FORCE_PASSWORD_RESET", "true");
        //When it is recreated with a different password
        props.put("DATABASE_PASSWORD", "test456");
        //Expect  a second attempt not to fail, even though it doesn't change
        testCreate(props, "test456");
    }

    @Test
    void testSingleAccess() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And the database has an existing schema
        props.put("DATABASE_USER", "existing");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmdForExistingSchema = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmdForExistingSchema.undo();
        cmdForExistingSchema.execute();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.execute();
        //Then the new user will not have access to create database objects in the existing schema
        try (Connection connection = DriverManager
                .getConnection("jdbc:postgresql://" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        "test123")) {
            PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE EXISTING.TEST_TABLE(ID NUMERIC )");
            try {
                preparedStatement.execute();
                fail();
            } catch (SQLException e) {
                CharArrayWriter caw = new CharArrayWriter();
                e.printStackTrace(new PrintWriter(caw));
                System.out.println(caw.toString());
                assertTrue(caw.toString().toLowerCase().contains("permission denied"));
            }
        }
    }

}