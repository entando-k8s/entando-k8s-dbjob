package org.entando.k8s.db.job;

import java.util.Optional;

public interface DatabaseAdminConfig {

    String getDatabaseAdminUser();

    String getDatabaseAdminPassword();

    String getDatabaseServerHost();

    String getDatabaseServerPort();

    String getDatabaseName();

    String getDatabaseUser();

    String getDatabasePassword();

    String getDatabaseVendor();

    Optional<String> getTablespace();

    Optional<String> getDatabaseIdentifierType();
}
