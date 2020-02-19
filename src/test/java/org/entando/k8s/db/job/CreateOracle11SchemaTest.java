package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

@Tags(@Tag("in-process"))
public class CreateOracle11SchemaTest extends CreateOracleSchemaTestBase {

    @Override
    protected String getPort() {
        return System.getenv("EXTERNAL_ORACLE11_SERVICE_HOST") == null ? "1522" : "1521";
    }

    @Override
    protected String getDatabaseName() {
        return "xe";
    }

    @Override
    protected String getAdminPassword() {
        return "oracle";
    }

    @Override
    protected String getAdminUser() {
        return "system";
    }

    @Override
    protected String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_ORACLE11_SERVICE_HOST")).orElse("localhost");
    }

}