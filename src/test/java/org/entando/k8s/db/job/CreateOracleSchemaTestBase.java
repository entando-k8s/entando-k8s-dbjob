package org.entando.k8s.db.job;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Test;

public abstract class CreateOracleSchemaTestBase {

    @Test
    public void simpleCreate() throws Exception {
        //Given I have admin rights and connectivity to a database
        Map<String, String> props = getBaseProperties();
        //And I specify a database user and password for which no schema exists yet
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        //And the schema/user combination does not yet exist
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.undo();
        //When I perform the CreateSchema command
        cmd.execute();
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = DriverManager
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TABLE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO MYSCHEMA.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    @Test
    public void testForcePasswordReset() throws Exception {
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props)).undo();
        //Given I have already created a schema
        testCreate(props, "test123");
        //And we enabled password resets
        props.put("FORCE_PASSWORD_RESET", "true");
        //When it is recreated with a different password
        props.put("DATABASE_PASSWORD", "test456");
        //Expect  a second attempt not to fail, even though it doesn't change
        testCreate(props, "test456");
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
        OracleDataSource ds = new OracleDataSource();
        ds.setServerName(getDatabaseServerHost());
        setDatabaseServiceName(ds);
        ds.setPortNumber(Integer.valueOf(getPort()));
        ds.setDriverType("thin");
        //Then the new user will have access to his own schema to create database objects
        try (Connection connection = ds.getConnection("myschema", "test123")) {
            connection.prepareStatement("CREATE TABLE TEST_TABLE(ID NUMERIC )").execute();
            connection.prepareStatement("TRUNCATE TABLE TEST_TABLE").execute();
            connection.prepareStatement("INSERT INTO myschema.TEST_TABLE (ID) VALUES (5)").execute();
            //and access them without having to specify the schema as prefix
            assertTrue(connection.prepareStatement("SELECT * FROM TEST_TABLE WHERE ID=5").executeQuery().next());
        }
    }

    protected abstract void setDatabaseServiceName(OracleDataSource ds);

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
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        "test123")) {
            connection.prepareStatement("CREATE TABLE EXISTING.TEST_TABLE(ID NUMERIC )").execute();
            fail();
        } catch (SQLException e) {
            CharArrayWriter caw = new CharArrayWriter();
            e.printStackTrace(new PrintWriter(caw));
            System.out.println(caw.toString());
            assertTrue(caw.toString().toLowerCase().contains("insufficient privileges"));
        }
    }

    @Test
    public void testIdempotent() throws Exception {
        Map<String, String> props = getBaseProperties();
        props.put("DATABASE_USER", "myschema");
        props.put("DATABASE_PASSWORD", "test123");
        new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props)).undo();
        //Given I have already created a schema
        testCreate(props, "test123");
        //Expect creating it a second time to succeed without breaking
        testCreate(props, "test123");
    }

    private void testCreate(Map<String, String> props, String password) throws SQLException {
        CreateSchemaCommand cmd = new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(props));
        cmd.execute();
        try (Connection connection = DriverManager
                .getConnection("jdbc:oracle:thin:@//" + getDatabaseServerHost() + ":" + getPort() + "/" + getDatabaseName(), "myschema",
                        password)) {
            assertTrue(connection.prepareStatement("SELECT 2 FROM DUAL").executeQuery().next());
        }
    }

    protected abstract String getPort();

    protected abstract String getDatabaseName();

    private Map<String, String> getBaseProperties() {
        //NB! These correspond to the ENV vars in docker-compose-cicd.yml
        Map<String, String> props = new HashMap<>();
        props.put("DATABASE_ADMIN_USER", getAdminUser());
        props.put("DATABASE_ADMIN_PASSWORD", getAdminPassword());
        //        props.put("DATABASE_ADMIN_USER", "admin");
        //        props.put("DATABASE_ADMIN_PASSWORD", "admin");
        props.put("DATABASE_SERVER_HOST", getDatabaseServerHost());
        props.put("DATABASE_SERVER_PORT", getPort());
        props.put("DATABASE_VENDOR", "oracle");
        props.put("DATABASE_NAME", getDatabaseName());
        props.put("JDBC_PARAMETERS", "oracle.jdbc.timezoneAsRegion=false");
        return props;
    }

    protected abstract String getAdminPassword();

    protected abstract String getAdminUser();

    protected abstract String getDatabaseServerHost();


}
