package com.brutecx.docflow_backend.config;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AuditCanonicalProperties.class
})
public class AuditCanonicalConfig {
}
