package org.entando.k8s.db.job;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static java.lang.String.format;

public enum DatabaseDialect {
    POSTGRESQL() {
        @Override
        public Connection connect(DatabaseAdminConfig config) throws SQLException {
            String url = format("jdbc:%s://%s:%s/%s", config.getDatabaseVendor(), config.getDatabaseServerHost(), config.getDatabaseServerPort(), config.getDatabaseName());
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        @Override

        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            statement.execute(format("CREATE USER %s WITH PASSWORD '%s'", config.getDatabaseUser(), config.getDatabasePassword()));
            statement.execute(format("CREATE SCHEMA %s AUTHORIZATION %s", config.getDatabaseUser(), config.getDatabaseUser()));
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP SCHEMA %s CASCADE", config.getDatabaseUser())));
            swallow(() -> st.execute(format("DROP USER %s", config.getDatabaseUser())));
        }
    };

    public static DatabaseDialect resolveFor(String vendorName) {
        return Enum.valueOf(DatabaseDialect.class, vendorName.toUpperCase());
    }

    public abstract Connection connect(DatabaseAdminConfig config) throws SQLException;

    public abstract void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException;

    public abstract void dropUserAndSchema(Statement st, DatabaseAdminConfig config);

    private static void swallow(SqlAction action) {
        try {
            action.execute();
        } catch (SQLException e) {
            System.out.println(e);
        }
    }

    private interface SqlAction {
        void execute() throws SQLException;
    }
}
