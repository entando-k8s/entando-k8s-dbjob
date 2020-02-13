package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Not executed because it requires a dedicated Oracle instance.
 */
public class CreateOracleSchemaTest {

    @Test
    public void simpleCreate() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "tes123");
        //And the schema/user combination does not yet exist
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.execute();
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = DriverManager
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":1521/" + getDatabaseName(), "myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TABLE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    @Test
    public void testIdempotent() throws Exception {
        //Given I have already created a schema
        testCreate();
        //Expect creating it a second time to succeed without breaking
        testCreate();
    }

    private void testCreate() throws SQLException {
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        try (Connection connection = DriverManager
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":1521/" + getDatabaseName(), "myschema", "test123")) {
            assertTrue(connection.prepareStatement("SELECT 2 FROM DUAL").executeQuery().next());
        }
    }

    private String getDatabaseName() {
        return "ORCLPDB1.localdomain";
    }

    private Map<String, String> getBaseProperties() {
        //NB! These correspond to the ENV vars in docker-compose-cicd.yml
        Map<String, String> props = new HashMap<>();
        props.put("DATABASE_ADMIN_USER", "admin");
        props.put("DATABASE_ADMIN_PASSWORD", "admin");
        props.put("DATABASE_SERVER_HOST", getDatabaseServerHost());
        props.put("DATABASE_SERVER_PORT", "1521");
        props.put("DATABASE_VENDOR", "oracle");
        props.put("DATABASE_NAME", getDatabaseName());
        return props;
    }

    private String getDatabaseServerHost() {
        return ofNullable(System.getenv("DATABASE_SERVER_HOST")).orElse("localhost");
    }

    @Test
    public void singleAccess() throws Exception {
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
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":1521/" + getDatabaseName(), "myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE EXISTING.TEST_TABLE(ID NUMERIC )").execute();
            fail();
        } catch (SQLException e) {
            CharArrayWriter caw = new CharArrayWriter();
            e.printStackTrace(new PrintWriter(caw));
            System.out.println(caw.toString());
            assertTrue(caw.toString().toLowerCase().contains("insufficient privileges"));
        }
    }

}