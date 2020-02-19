package org.entando.k8s.db.job;

import static java.util.Optional.ofNullable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

@Tags(@Tag("in-process"))
public class CreateOracleSchemaTest extends CreateOracleSchemaTestBase {

    @Override
    protected String getPort() {
        return "1521";
    }

    @Override
    protected String getDatabaseName() {
        return "ORCLPDB1.localdomain";
    }

    @Override
    protected String getAdminPassword() {
        return "admin";
    }

    @Override
    protected String getAdminUser() {
        return "admin";
    }

    @Override
    protected String getDatabaseServerHost() {
        return ofNullable(System.getenv("EXTERNAL_ORACLE_SERVICE_HOST")).orElse("localhost");
    }

}