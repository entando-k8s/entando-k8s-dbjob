package org.entando.k8s.db.job;

public interface DatabaseAdminConfig {
    String getDatabaseAdminUser();
    String getDatabaseAdminPassword();
    String getDatabaseServerHost();
    String getDatabaseServerPort();
    String getDatabaseName();
    String getDatabaseUser();
    String getDatabasePassword();
    String getDatabaseVendor();

}
