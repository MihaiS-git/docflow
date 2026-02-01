package com.brutecx.docflow_backend.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Keycloak admin user pull.
 */
@ConfigurationProperties(prefix = "docflow.security.keycloak.admin")
public record KeycloakAdminPullProperties (
    String baseUrl,
    String realm,
    String clientId,
    String clientSecret,
    long pollFixedDelayMs,
    int pageSize
){}
