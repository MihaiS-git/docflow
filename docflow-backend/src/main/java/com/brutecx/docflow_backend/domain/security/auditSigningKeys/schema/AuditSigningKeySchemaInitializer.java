package com.brutecx.docflow_backend.domain.security.auditSigningKeys.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup validation for audit signing keys.
 * NOTE:
 * - Partial unique index must be enforced via Flyway migration.
 * - This component only validates DB integrity at startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class AuditSigningKeySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void validateInvariants() {

        validateSingleActiveKey();

        log.info("Audit signing key DB validation completed");
    }

    private void validateSingleActiveKey() {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_signing_keys WHERE active = true",
                Integer.class
        );

        if (count != null && count > 1) {
            throw new IllegalStateException(
                    "DB corruption detected: multiple active audit signing keys found (" + count + ")"
            );
        }
    }
}