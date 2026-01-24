package com.brutecx.docflow_backend.security.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for session security.
 */
@ConfigurationProperties(prefix = "docflow.security.session")
public record SessionSecurityProperties (
        Duration absoluteTimeout,
        int maxConcurrentSessions
){}
