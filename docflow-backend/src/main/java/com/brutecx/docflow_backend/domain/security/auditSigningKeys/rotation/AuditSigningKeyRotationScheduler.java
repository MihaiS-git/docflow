package com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "docflow.audit.signing.rotation",
        name = "enabled",
        havingValue = "true"
)
public class AuditSigningKeyRotationScheduler {

    private final AuditSigningKeyRotationService rotationService;

    @Scheduled(cron = "${docflow.audit.signing.rotation.cron}")
    public void runRotationJob() {
        try {
            rotationService.rotateIfRequired();
        } catch (Exception ex) {
            log.error("security_event {}",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "key_management"),
                    kv("event.action", "audit_export_key_rotation_scheduler"),
                    kv("event.outcome", "failure"),
                    kv("error", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }
}