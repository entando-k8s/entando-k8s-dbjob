package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.quarkus.runtime.StartupEvent;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(@Tag("integration"))
class CreateMysqlSchemaTest {

    @Test
    void testSimpleCreate() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        //And the given user/schema combination does not exist
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.onStartup(new StartupEvent());
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = DriverManager
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":" + getDatabaseServerPort() + "/myschema", "myschema",
                        "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO myschema.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    @Test
    void testIdempotent() throws Exception {
        //Given that a specific user/schema combination does not exist
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //But then it is created
        testCreate(props, "test123");
        //Expect a second attempt not to fail, even though it doesn't change
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

    private void testCreate(Map<String, String> props, String password) throws Exception {
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.execute();
        try (Connection connection = DriverManager
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":" + getDatabaseServerPort() + "/myschema", "myschema",
                        password)) {
            assertTrue(connection.prepareStatement("SELECT 1 FROM  DUAL").executeQuery().next());
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
        MysqlDataSource ds = new MysqlDataSource();
        ds.setServerName(getDatabaseServerHost());
        ds.setDatabaseName("myschema");
        ds.setPortNumber(Integer.valueOf(getDatabaseServerPort()));
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = ds.getConnection("myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO myschema.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    private Map<String, String> getBaseProperties() {
        //NB! These correspond to the ENV vars in docker-compose-cicd.yml
        Map<String, String> props = new HashMap<>();
        props.put("DATABASE_ADMIN_USER", TestConfigProperty.MYSQL_ADMIN_USER.resolve());
        props.put("DATABASE_ADMIN_PASSWORD", TestConfigProperty.MYSQL_ADMIN_PASSWORD.resolve());
        props.put("DATABASE_SERVER_HOST", getDatabaseServerHost());
        props.put("DATABASE_SERVER_PORT", getDatabaseServerPort());
        props.put("DATABASE_VENDOR", "mysql");
        props.put("JDBC_PARAMETERS", "useSSL=false");
        return props;
    }

    private String getDatabaseServerPort() {
        return ofNullable(System.getenv("EXTERNAL_MYSQL_SERVICE_PORT")).orElse("3306");
    }

    private String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_MYSQL_SERVICE_HOST")).orElse("localhost");
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
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":" + getDatabaseServerPort() + "/myschema", "myschema",
                        "test123")) {
            PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE existing.TEST_TABLE(ID NUMERIC )");
            try {
                preparedStatement.execute();
                fail();
            } catch (SQLException e) {
                CharArrayWriter caw = new CharArrayWriter();
                e.printStackTrace(new PrintWriter(caw));
                System.out.println(caw.toString());
                assertTrue(caw.toString().toLowerCase().contains("command denied"));
            }
        }
    }
}