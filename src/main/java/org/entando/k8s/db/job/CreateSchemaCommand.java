package org.entando.k8s.db.job;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import picocli.CommandLine;

@ApplicationScoped
@CommandLine.Command()
public class CreateSchemaCommand implements Runnable {

    
    private static final Logger LOGGER = Logger.getLogger(CreateSchemaCommand.class.getName());
    private final DatabaseAdminConfig databaseAdminConfig;

    public CreateSchemaCommand() {
        this.databaseAdminConfig = new PropertiesBasedDatabaseAdminConfig(System.getenv());
    }

    public CreateSchemaCommand(DatabaseAdminConfig databaseAdminConfig) {
        this.databaseAdminConfig = databaseAdminConfig;
    }

    public void run() {
        LOGGER.severe("onStartup");
        try {
            execute();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Schema creation failed.", e);
            throw new CommandLine.ExecutionException(new CommandLine(this), e.getMessage());
        } finally {
            LOGGER.severe("onStartup:finally");
        }
    }

    public void execute() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        if (!userCredentialsValid(dialect)) {
            if (dialect.schemaExists(databaseAdminConfig)) {
                if (databaseAdminConfig.forcePasswordReset()) {
                    resetPassword(dialect);
                }
            } else {
                createNewUser(dialect);
            }
        }
    }

    private void createNewUser(DatabaseDialect dialect) throws SQLException {
        try (Connection connection = dialect.connectAsAdmin(this.databaseAdminConfig)) {
            Statement st = connection.createStatement();
            dialect.createUserAndSchema(st, this.databaseAdminConfig);
        }
    }

    private void resetPassword(DatabaseDialect dialect) throws SQLException {
        try (Connection connection = dialect.connectAsAdmin(this.databaseAdminConfig)) {
            Statement st = connection.createStatement();
            dialect.resetPassword(st, this.databaseAdminConfig);
        }
    }

    private boolean userCredentialsValid(DatabaseDialect dialect) {
        try {
            Connection connection = dialect.connectAsUser(databaseAdminConfig);
            boolean valid = connection.isValid(5000);
            connection.close();
            return valid;
        } catch (SQLException e) {
            return false;
        }
    }

    public void undo() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        try (Connection connection = dialect.connectAsAdmin(this.databaseAdminConfig)) {
            Statement st = connection.createStatement();
            dialect.dropUserAndSchema(st, this.databaseAdminConfig);
        }
    }

}

