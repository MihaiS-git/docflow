package com.brutecx.docflow_backend.domain.audit.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuditRetentionSystemActorProperties.class)
public class AuditRetentionConfig {
}
