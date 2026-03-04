package com.brutecx.docflow_backend.domain.audit.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled runner for audit retention enforcement.
 * Auditor-grade properties:
 * - property-gated (can be disabled in strict deployments)
 * - delegates to service which ensures single-instance execution via PG advisory lock
 * - no business logic here
 */
@Component
@ConditionalOnProperty(
        prefix = "docflow.audit.retention",
        name = "enabled",
        havingValue = "true"
)
public class AuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuditRetentionEnforcementService retentionService;

    public AuditRetentionScheduler(AuditRetentionEnforcementService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${docflow.audit.retention.cron:0 15 2 * * *}", zone = "UTC")
    public void run() {
        try {
            retentionService.enforceAllStreams();
        } catch (Exception ex) {
            // Never crash scheduler threads; log with context.
            log.error("audit_retention enforcement_failed: {}", ex.getMessage(), ex);
        }
    }
}