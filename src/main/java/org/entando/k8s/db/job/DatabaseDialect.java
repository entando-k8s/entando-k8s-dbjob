package org.entando.k8s.db.job;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public enum DatabaseDialect {
    MYSQL() {
        @Override
        public Connection connectAsUser(DatabaseAdminConfig config) throws SQLException {
            String url = format("jdbc:mysql://%s:%s/%s%s",
                    config.getDatabaseServerHost(),
                    config.getDatabaseServerPort(),
                    config.getDatabaseUser(),
                    parameterSuffix(config.getJdbcParameters()));
            return DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword());
        }

        @Override
        public Connection connectAsAdmin(DatabaseAdminConfig config) throws SQLException {
            String url = format("jdbc:mysql://%s:%s%s",
                    config.getDatabaseServerHost(),
                    config.getDatabaseServerPort(),
                    parameterSuffix(config.getJdbcParameters()));
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            try {
                String url = format("jdbc:mysql://%s:%s/%s%s",
                        config.getDatabaseServerHost(),
                        config.getDatabaseServerPort(),
                        config.getDatabaseUser(),
                        parameterSuffix(config.getJdbcParameters()));
                DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword()).close();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        @Override
        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            statement.execute(format("CREATE DATABASE  %s", config.getDatabaseUser()));
            statement.execute(
                    format("CREATE USER '%s'@'%%' IDENTIFIED BY '%s';",
                            config.getDatabaseUser(),
                            config.getDatabasePassword()));
            statement.execute(
                    format("GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%' WITH GRANT OPTION;",
                            config.getDatabaseUser(),
                            config.getDatabaseUser()));
            if (statement.getConnection().getMetaData().getDatabaseMajorVersion() >= 8) {
                //See https://stackoverflow.com/questions/56831529/configuring-a-xa-datasource-to-mysql-8-db-with-spring-boot-and
                // -bitronix-jta-mana
                statement.execute(
                        format("GRANT XA_RECOVER_ADMIN ON *.* TO '%s'@'%%';",
                                config.getDatabaseUser()));
            }
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP DATABASE IF EXISTS %s", config.getDatabaseUser())));
            swallow(() -> st.execute(format("DROP USER '%s'@'%%'", config.getDatabaseUser())));
        }

        @Override
        public void resetPassword(Statement st, DatabaseAdminConfig databaseAdminConfig) throws SQLException {
            st.execute(format("ALTER USER %s IDENTIFIED BY '%s';", databaseAdminConfig.getDatabaseUser(),
                    databaseAdminConfig.getDatabasePassword()));
        }
    },
    POSTGRESQL() {
        @Override
        public Connection connectAsUser(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword());
        }

        public Connection connectAsAdmin(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        private String buildUrl(DatabaseAdminConfig config) {
            return format("jdbc:postgresql://%s:%s/%s%s",
                    config.getDatabaseServerHost(),
                    config.getDatabaseServerPort(),
                    config.getDatabaseName(),
                    parameterSuffix(config.getJdbcParameters())
            );
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            try (Connection connection = DriverManager
                    .getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword())) {
                try (ResultSet resultSet = connection.createStatement()
                        .executeQuery(format("SELECT COUNT(*) FROM PG_ROLES WHERE ROLNAME='%s'", config.getDatabaseUser()))) {
                    resultSet.next();
                    return resultSet.getInt(1) == 1;
                }
            }
        }

        @Override
        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
            statement.execute(format("CREATE USER \"%s\" WITH PASSWORD '%s'", config.getDatabaseUser(), config.getDatabasePassword()));
            statement.execute(format("GRANT \"%s\" TO \"%s\"", config.getDatabaseUser(), config.getDatabaseAdminUser()));
            statement.execute(format("CREATE SCHEMA \"%s\" AUTHORIZATION \"%s\"", config.getDatabaseUser(), config.getDatabaseUser()));
            statement.execute(format("ALTER ROLE \"%s\" SET search_path = \"%s\"", config.getDatabaseUser(), config.getDatabaseUser()));
        }

        @Override
        public void dropUserAndSchema(Statement st, DatabaseAdminConfig config) {
            swallow(() -> st.execute(format("DROP SCHEMA \"%s\" CASCADE", config.getDatabaseUser())));
            swallow(() -> st.execute(format("DROP USER \"%s\"", config.getDatabaseUser())));
        }

        @Override
        public void resetPassword(Statement st, DatabaseAdminConfig con) throws SQLException {
            st.execute(format("ALTER USER \"%s\" WITH PASSWORD '%s'", con.getDatabaseUser(), con.getDatabasePassword()));
        }
    },
    ORACLE() {
        @Override
        public Connection connectAsUser(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword());
        }

        @Override
        public Connection connectAsAdmin(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            return DriverManager.getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword());
        }

        @Override
        public boolean schemaExists(DatabaseAdminConfig config) throws SQLException {
            String url = buildUrl(config);
            try (Connection connection = DriverManager
                    .getConnection(url, config.getDatabaseAdminUser(), config.getDatabaseAdminPassword())) {
                try (ResultSet resultSet = connection.createStatement()
                        .executeQuery(
                                format("SELECT COUNT(*) FROM DBA_USERS WHERE USERNAME='%s'", config.getDatabaseUser().toUpperCase()))) {
                    resultSet.next();
                    return resultSet.getInt(1) == 1;
                }
            }
        }

        private String buildUrl(DatabaseAdminConfig config) {
            return format("jdbc:oracle:thin:@//%s:%s/%s%s",
                    config.getDatabaseServerHost(),
                    config.getDatabaseServerPort(),
                    config.getDatabaseName(),
                    parameterSuffix(config.getJdbcParameters()));
        }

        @Override
        public void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException {
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

        @Override
        public void resetPassword(Statement st, DatabaseAdminConfig databaseAdminConfig) throws SQLException {
            st.execute(format("ALTER USER %s IDENTIFIED BY %s", databaseAdminConfig.getDatabaseUser(),
                    databaseAdminConfig.getDatabasePassword()));
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

    public abstract Connection connectAsUser(DatabaseAdminConfig config) throws SQLException;

    public abstract Connection connectAsAdmin(DatabaseAdminConfig config) throws SQLException;

    public abstract boolean schemaExists(DatabaseAdminConfig config) throws SQLException;

    public abstract void createUserAndSchema(Statement statement, DatabaseAdminConfig config) throws SQLException;

    public abstract void dropUserAndSchema(Statement st, DatabaseAdminConfig config);

    protected String parameterSuffix(List<String> jdbcParameters) {
        if (jdbcParameters.isEmpty()) {
            return "";
        } else {
            return "?" + String.join("&", jdbcParameters);
        }
    }

    public abstract void resetPassword(Statement st, DatabaseAdminConfig databaseAdminConfig) throws SQLException;

    private interface SqlAction {

        void execute() throws SQLException;
    }
}
