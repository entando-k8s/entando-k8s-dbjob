package org.entando.k8s.db.job;

import io.quarkus.runtime.StartupEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

public class CreateSchemaCommand {

    private static final Logger logger = Logger.getLogger(CreateSchemaCommand.class.getName());
    private DatabaseAdminConfig databaseAdminConfig;
    private int status = 0;

    @Inject
    public CreateSchemaCommand(DatabaseAdminConfig databaseAdminConfig) {
        this.databaseAdminConfig = databaseAdminConfig;
    }

    public void onStart(@Observes StartupEvent startupEvent) {
        try {
            execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Schema creation failed.", e);
            status = -1;
        } finally {
            new Thread(() -> {
                System.exit(status);
            }).start();
        }
    }

    public void execute() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        if (!dialect.schemaExists(databaseAdminConfig)) {
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

