package com.brutecx.docflow_backend.domain.security.auditSigningKeys.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Enforces DB-level invariants for audit signing keys:
 * 1) At most one active key (partial unique index)
 * 2) Startup validation to detect corruption
 * Runs AFTER JPA/Hibernate schema initialization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class AuditSigningKeySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void enforceInvariants() {

        ensurePartialUniqueIndex();

        validateSingleActiveKey();

        log.info("Audit signing key DB invariants verified");
    }

    private void ensurePartialUniqueIndex() {

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_signing_keys_active_true
            ON audit_signing_keys (active)
            WHERE active = true
        """);

        log.info("Ensured partial unique index ux_audit_signing_keys_active_true");
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