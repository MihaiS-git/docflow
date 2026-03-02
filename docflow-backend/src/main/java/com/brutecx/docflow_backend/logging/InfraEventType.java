package com.brutecx.docflow_backend.logging;

public enum InfraEventType {

    AUTHENTICATION(
            "authentication",
            InfraSeverity.INFO,
            InfraComplianceClass.SECURITY
    ),

    AUTHORIZATION(
            "authorization",
            InfraSeverity.WARN,
            InfraComplianceClass.SECURITY
    ),

    MAIL(
            "mail",
            InfraSeverity.INFO,
            InfraComplianceClass.OPERATIONAL
    ),

    DATABASE(
            "database",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    ),

    UPSTREAM(
            "upstream",
            InfraSeverity.WARN,
            InfraComplianceClass.SYSTEM
    ),

    MESSAGING(
            "messaging",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    ),

    CACHE(
            "cache",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    ),

    STORAGE(
            "storage",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    ),

    SCHEDULER(
            "scheduler",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    ),

    CRYPTOGRAPHY(
            "cryptography",
            InfraSeverity.INFO,
            InfraComplianceClass.SECURITY
    ),

    CONFIGURATION(
            "configuration",
            InfraSeverity.WARN,
            InfraComplianceClass.SYSTEM
    ),

    STARTUP(
            "startup",
            InfraSeverity.INFO,
            InfraComplianceClass.SYSTEM
    );

    private final String category;
    private final InfraSeverity defaultSeverity;
    private final InfraComplianceClass complianceClass;

    InfraEventType(
            String category,
            InfraSeverity defaultSeverity,
            InfraComplianceClass complianceClass
    ) {
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.complianceClass = complianceClass;
    }

    public String value() {
        return category;
    }

    public String category() {
        return category;
    }

    public InfraSeverity defaultSeverity() {
        return defaultSeverity;
    }

    public InfraComplianceClass complianceClass() {
        return complianceClass;
    }
}