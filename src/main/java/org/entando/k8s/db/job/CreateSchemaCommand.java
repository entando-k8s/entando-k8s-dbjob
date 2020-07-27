package org.entando.k8s.db.job;

import io.quarkus.runtime.StartupEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class CreateSchemaCommand {

    private static final Logger LOGGER = Logger.getLogger(CreateSchemaCommand.class.getName());
    private DatabaseAdminConfig databaseAdminConfig;
    private int status = 0;
    private Runnable autoExit = () -> {
    };

    public CreateSchemaCommand() {
        autoExit = () -> System.exit(status);
        this.databaseAdminConfig = new PropertiesBasedDatabaseAdminConfig(System.getenv());
    }

    public CreateSchemaCommand(DatabaseAdminConfig databaseAdminConfig) {
        this.databaseAdminConfig = databaseAdminConfig;
    }

    public void onStartup(@Observes StartupEvent startupEvent) {
        LOGGER.severe("onStartup");
        try {
            execute();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Schema creation failed.", e);
            status = -1;
        } finally {
            LOGGER.severe("onStartup:finally");
            new Thread(autoExit).start();
        }
    }

    public void execute() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        if (dialect.schemaExists(databaseAdminConfig)) {
            if (databaseAdminConfig.forcePasswordReset()) {
                try (Connection connection = dialect.connect(this.databaseAdminConfig)) {
                    Statement st = connection.createStatement();
                    dialect.resetPassword(st, this.databaseAdminConfig);
                }
            }
        } else {
            try (Connection connection = dialect.connect(this.databaseAdminConfig)) {
                Statement st = connection.createStatement();
                dialect.createUserAndSchema(st, this.databaseAdminConfig);
            }
        }
    }

    public void undo() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        try (Connection connection = dialect.connect(this.databaseAdminConfig)) {
            Statement st = connection.createStatement();
            dialect.dropUserAndSchema(st, this.databaseAdminConfig);
        }
    }

}

