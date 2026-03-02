package com.brutecx.docflow_backend.security.session;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for session security.
 * Fail-fast validated at startup.
 */
@Validated
@ConfigurationProperties(prefix = "docflow.security.session")
public record SessionSecurityProperties(

        /* Absolute maximum lifetime of a session.
         If null or zero, absolute timeout enforcement is disabled. */
        Duration absoluteTimeout,

        /* Maximum concurrent sessions per subject.
         Must be >= 1. */
        @Min(1)
        int maxConcurrentSessions

) {}