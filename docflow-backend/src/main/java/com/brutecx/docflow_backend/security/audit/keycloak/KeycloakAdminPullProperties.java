package com.brutecx.docflow_backend.security.audit.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docflow.security.keycloak.admin")
public record KeycloakAdminPullProperties (
    String baseUrl,
    String realm,
    String clientId,
    String clientSecret,
    long pollFixedDelayMs,
    int pageSize
){}
