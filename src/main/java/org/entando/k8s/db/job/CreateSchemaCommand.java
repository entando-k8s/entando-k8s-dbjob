package org.entando.k8s.db.job;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class CreateSchemaCommand {
    private DatabaseAdminConfig databaseAdminConfig;

    public CreateSchemaCommand(DatabaseAdminConfig databaseAdminConfig) {

        this.databaseAdminConfig = databaseAdminConfig;
    }

    public void execute() throws SQLException {
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        try(Connection connection = dialect.connect(this.databaseAdminConfig) ){
            Statement st = connection.createStatement();
            dialect.createUserAndSchema(st, this.databaseAdminConfig);
        }
    }
    public void undo() throws SQLException{
        DatabaseDialect dialect = DatabaseDialect.resolveFor(databaseAdminConfig.getDatabaseVendor());
        try(Connection connection = dialect.connect(this.databaseAdminConfig) ){
            Statement st = connection.createStatement();
            dialect.dropUserAndSchema(st,this.databaseAdminConfig);
        }
    }

    public static void main(String[] args) throws SQLException {
        new CreateSchemaCommand(new PropertiesBasedDatabaseAdminConfig(System.getenv())).execute();
    }
}

