package org.entando.k8s.db.job;

import java.util.List;
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

    List<String> getJdbcParameters();

    boolean forcePasswordReset();
}
