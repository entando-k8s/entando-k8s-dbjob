package org.entando.k8s.db.job;

import java.util.Map;
import java.util.Optional;


public class PropertiesBasedDatabaseAdminConfig implements DatabaseAdminConfig{
    private final Map<String, String> properties;

    public PropertiesBasedDatabaseAdminConfig(Map<String, String> properties) {
        this.properties = properties;
    }

    protected Map<String,String> getProperties(){
        return properties;
    }

    @Override
    public String getDatabaseAdminUser() {
        return getProperties().get("DATABASE_ADMIN_USER");
    }

    @Override
    public String getDatabaseAdminPassword() {
        return getProperties().get("DATABASE_ADMIN_PASSWORD");
    }

    @Override
    public String getDatabaseServerHost() {
        return getProperties().get("DATABASE_SERVER_HOST");
    }

    @Override
    public String getDatabaseServerPort() {
        return getProperties().get("DATABASE_SERVER_PORT");
    }

    @Override
    public String getDatabaseName() {
        return getProperties().get("DATABASE_NAME");
    }

    @Override
    public String getDatabaseUser() {
        return getProperties().get("DATABASE_USER");
    }

    @Override
    public String getDatabasePassword() {
        return getProperties().get("DATABASE_PASSWORD");
    }

    @Override
    public String getDatabaseVendor() {
        return getProperties().get("DATABASE_VENDOR");
    }

    @Override
    public Optional<String> getTablespace() {
        return Optional.ofNullable(getProperties().get("TABLESPACE"));
    }
}
