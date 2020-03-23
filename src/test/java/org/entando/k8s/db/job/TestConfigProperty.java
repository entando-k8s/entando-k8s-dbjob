package org.entando.k8s.db.job;

public enum TestConfigProperty {
    MYSQL_ADMIN_USER("mysql.admin.user", "root"),
    MYSQL_ADMIN_PASSWORD("mysql.admin.password", "Password1"),
    ORACLE11_ADMIN_USER("oracle11.admin.user", "system"),
    ORACLE11_ADMIN_PASSWORD("oracle11.admin.password", "oracle123"),
    ORACLE11_DATABASE_NAME("oracle11.database.name", "xe"),
    ORACLE12_ADMIN_USER("oracle12.admin.user", "admin"),
    ORACLE12_ADMIN_PASSWORD("oracle12.admin.password", "admin"),
    ORACLE12_DATABASE_NAME("oracle12.database.name", "ORCLPDB1.localdomain"),
    POSTGRESQL_ADMIN_USER("postgresql.admin.user", "postgres"),
    POSTGRESQL_ADMIN_PASSWORD("postgresql.admin.password", "postgres"),
    POSTGRESQL_DATABASE_NAME("postgresql.database.name", "sampledb");
    private final String jvmProperty;
    private final String defaultValue;

    TestConfigProperty(String s, String defaultValue) {
        jvmProperty = s;
        this.defaultValue = defaultValue;
    }

    public String resolve() {
        String value = System.getenv(name());
        if (value == null) {
            value = System.getProperty(jvmProperty);
            if (value == null) {
                value = defaultValue;
            }
        }
        return value;
    }
}

