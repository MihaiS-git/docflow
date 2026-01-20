package com.brutecx.docflow_backend.security.audit.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(KeycloakAdminPullProperties.class)
public class KeycloakAdminPullConfiguration {
}
