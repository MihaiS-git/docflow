package com.brutecx.docflow_backend.audit.tamper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditChainSecretStartupGuard {

    private final AuditChainSecretProvider secretProvider;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void validateSecretConsistency() {

        String secret = secretProvider.getResolvedSecretOrEmpty();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Audit chain secret resolved to empty value");
        }

        String fingerprint = fingerprint(secret);

        log.info("security_event {}",
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", "audit_chain_secret_loaded"),
                kv("event.outcome", "success"),
                kv("audit.chain.secret_fingerprint", fingerprint)
        );

        Boolean eventsExist = jdbcTemplate.query(
                "select 1 from admin_audit_events limit 1",
                (ResultSetExtractor<Boolean>) rs -> rs.next()
        );

        if (Boolean.TRUE.equals(eventsExist)) {
            log.info("security_event {}",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "audit_chain_secret_validation"),
                    kv("event.outcome", "success"),
                    kv("audit.chain.events_detected", true)
            );
        }
    }

    private static String fingerprint(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute audit chain secret fingerprint", e);
        }
    }
}