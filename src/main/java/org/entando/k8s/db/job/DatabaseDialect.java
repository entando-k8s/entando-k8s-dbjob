package org.entando.k8s.db.job;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public enum DatabaseDialect {
    MYSQL() {
        @Override
        public Connection connect(DatabaseAdminConfig config) throws SQLException {
            String url = format("jdbc:mysql://%s:%s", config.getDatabaseServerHost(), config.getDatabaseServerPort());
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            try {
                String url = format("jdbc:mysql://%s:%s/%s",
                        config.getDatabaseServerHost(),
                        config.getDatabaseServerPort(),
                        config.getDatabaseUser());
                DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword()).close();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        @Override

        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            statement.execute(format("CREATE DATABASE  %s", config.getDatabaseUser()));
            statement.execute(
                    format("GRANT ALL PRIVILEGES  ON %s.*  TO '%s'@'%%' IDENTIFIED BY '%s'  WITH GRANT OPTION;",
                            config.getDatabaseUser(),
                            config.getDatabaseUser(),
                            config.getDatabasePassword()));
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP DATABASE IF EXISTS %s", config.getDatabaseUser())));
            swallow(() -> st.execute(format("DROP USER '%s'@'%%'", config.getDatabaseUser())));
        }
    },
    POSTGRESQL() {
        @Override
        public Connection connect(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        private String buildUrl(DatabaseAdminConfig config) {
            return format("jdbc:postgresql://%s:%s/%s", config.getDatabaseServerHost(), config.getDatabaseServerPort(),
                    config.getDatabaseName());
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            try {
                String url = buildUrl(config);
                DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword()).close();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        @Override

        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            statement.execute(format("CREATE USER %s WITH PASSWORD '%s'", config.getDatabaseUser(), config.getDatabasePassword()));
            statement.execute(format("CREATE SCHEMA %s AUTHORIZATION %s", config.getDatabaseUser(), config.getDatabaseUser()));
            statement.execute(format("ALTER ROLE %s SET search_path =  %s ", config.getDatabaseUser(), config.getDatabaseUser()));
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP SCHEMA %s CASCADE", config.getDatabaseUser())));
            swallow(() -> st.execute(format("DROP USER %s", config.getDatabaseUser())));
        }
    },
    ORACLE() {
        public static final String FORMAT = "jdbc:oracle:thin:@//%s:%s/%s";

        @Override
        public Connection connect(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            try {
                String url = buildUrl(config);
                DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword()).close();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        private String buildUrl(DatabaseAdminConfig config) {
            return format(FORMAT, config.getDatabaseServerHost(), config.getDatabaseServerPort(),
                    config.getDatabaseName());
        }

        @Override

        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            //            statement.execute("alter session set \"_ORACLE_SCRIPT\"=true;");
            statement.execute(format("CREATE USER %s IDENTIFIED BY \"%s\"", config.getDatabaseUser(), config.getDatabasePassword()));
            String sql = format(
                    "GRANT CREATE PROCEDURE, CREATE TRIGGER, CREATE PUBLIC SYNONYM, CREATE SEQUENCE, CREATE SESSION, CREATE SYNONYM,"
                            + "CREATE TABLE, CREATE VIEW TO %s",
                    config.getDatabaseUser());
            statement.execute(sql);
            statement.execute(
                    format("ALTER USER %s quota unlimited on %s ", config.getDatabaseUser(), config.getTablespace().orElse("USERS")));
            if (config.getTablespace().orElse("SYSTEM").equalsIgnoreCase("system")) {
                //On Oracle 11 on occasion the user needs quota allocation here
                statement.execute(format("ALTER USER %s quota 50m on system", config.getDatabaseUser()));
            }
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP USER %s CASCADE", config.getDatabaseUser())));
        }
    };

    public static DatabaseDialect resolveFor(String vendorName) {
        return Enum.valueOf(DatabaseDialect.class, vendorName.toUpperCase());
    }

    private static void swallow(SqlAction action) {
        try {
            action.execute();
        } catch (SQLException e) {
            Logger.getAnonymousLogger().log(Level.WARNING, "Exception ignored.", e);
        }
    }

    public abstract Connection connect(DatabaseAdminConfig config) throws SQLException;

    public abstract boolean schemaExists(DatabaseAdminConfig config) throws SQLException;

    public abstract void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException;

    public abstract void dropUserAndSchema(Statement st, DatabaseAdminConfig config);

    private interface SqlAction {

        void execute() throws SQLException;
    }
}
