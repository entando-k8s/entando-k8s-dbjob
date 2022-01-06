package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

@Disabled
@Tags(@Tag("integration"))
class CreateOracle12SchemaTest extends CreateOracleSchemaTestBase {

    @Override
    protected String getPort() {
        return ofNullable(System.getenv("EXTERNAL_ORACLE_SERVICE_PORT")).orElse("1521");

    }

    @Override
    protected String getDatabaseName() {
        return TestConfigProperty.ORACLE12_DATABASE_NAME.resolve();
    }

    @Override
    protected String getAdminPassword() {
        return TestConfigProperty.ORACLE12_ADMIN_PASSWORD.resolve();
    }

    @Override
    protected String getAdminUser() {
        return TestConfigProperty.ORACLE12_ADMIN_USER.resolve();
    }

    protected void setDatabaseServiceName(OracleDataSource ds) {
        ds.setServiceName(getDatabaseName());
    }

    @Override
    protected String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_ORACLE_SERVICE_HOST")).orElse("localhost");
    }

}