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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(@Tag("in-process"))
public class CreateMysqlSchemaTest {

    @Test
    public void testSimpleCreate() throws Exception {
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
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":3306/myschema", "myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO myschema.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    @Test
    public void testIdempotent() throws Exception {
        //Give that a specific user/schema combination does not exist
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //But then it is created
        testCreate(props);
        //Expected a second attempt not to fail, even though it doesn't change
        testCreate(props);
    }

    private void testCreate(Map<String, String> props) throws Exception {
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.execute();
        try (Connection connection = DriverManager
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":3306/myschema", "myschema", "test123")) {
            assertTrue(connection.prepareStatement("SELECT 1 FROM  DUAL").executeQuery().next());
        }
    }

    @Test
    public void testDatasource() throws Exception {
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
        ds.setPortNumber(3306);
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
        props.put("DATABASE_ADMIN_USER", "root");
        props.put("DATABASE_ADMIN_PASSWORD", "Password1");
        props.put("DATABASE_SERVER_HOST", getDatabaseServerHost());
        props.put("DATABASE_SERVER_PORT", "3306");
        props.put("DATABASE_VENDOR", "mysql");
        return props;
    }

    private String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_MYSQL_SERVICE_HOST")).orElse("localhost");
    }

    @Test
    public void testSingleAccess() throws Exception {
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
                .getConnection("jdbc:mysql://" + getDatabaseServerHost() + ":3306/myschema", "myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE existing.TEST_TABLE(ID NUMERIC )").execute();
            fail();
        } catch (SQLException e) {
            CharArrayWriter caw = new CharArrayWriter();
            e.printStackTrace(new PrintWriter(caw));
            System.out.println(caw.toString());
            assertTrue(caw.toString().toLowerCase().contains("command denied"));
        }
    }

}