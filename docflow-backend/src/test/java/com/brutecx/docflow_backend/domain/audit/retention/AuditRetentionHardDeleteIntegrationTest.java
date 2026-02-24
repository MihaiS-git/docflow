package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditRetentionHardDeleteIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AuditRetentionEnforcementService service;

    @Autowired
    AuditRetentionPolicyRepository policyRepository;

    @Autowired
    SealedJsonlAuditExportFileService exportFileService;

    @BeforeEach
    void setup() {

        policyRepository.deleteAll();

        policyRepository.save(
                new AuditRetentionPolicy(
                        "AUTHENTICATION",
                        7,
                        true
                )
        );

        // Completely bypass signing + rotation layer
        when(exportFileService.exportByIds(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyList(),
                Mockito.any()
        )).thenAnswer(invocation -> {

            List<?> ids = invocation.getArgument(2);

            return new SealedJsonlAuditExportFileService.ExportResult(
                    UUID.randomUUID(),
                    Path.of("build/test.jsonl"),
                    ids.size(),
                    "dummy-digest",
                    "test-key"
            );
        });
    }

    @Test
    void export_then_delete_occurs() {

        UUID id = UUID.randomUUID();
        insertValidAuthenticationEvent(id, "corr-1");

        service.enforceAllStreams();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authentication_events WHERE id = ?",
                Integer.class,
                id
        );

        assertThat(count).isZero();
    }

    @Test
    void legal_hold_blocks_delete() {

        UUID id = UUID.randomUUID();
        insertValidAuthenticationEvent(id, "corr-2");

        jdbcTemplate.update(
                "INSERT INTO audit_legal_holds (" +
                        "id, stream_name, event_id, case_reference_id, reason, created_by, created_at, active" +
                        ") VALUES (?, ?, ?, ?, ?, ?, now(), true)",
                UUID.randomUUID(),
                "AUTHENTICATION",
                id,
                "CASE-1",
                "test",
                "system"
        );

        service.enforceAllStreams();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authentication_events WHERE id = ?",
                Integer.class,
                id
        );

        assertThat(count).isEqualTo(1);
    }

    private void insertValidAuthenticationEvent(UUID id, String correlationId) {

        Timestamp oldTimestamp =
                Timestamp.from(Instant.parse("2020-01-01T00:00:00Z"));

        jdbcTemplate.update(
                "INSERT INTO authentication_events (" +
                        "id, source, timestamp, username, subject_id, authentication_result, idp, ip, user_agent, " +
                        "correlation_id, correlation_source, execution_context, audit_result, event_fingerprint, " +
                        "chain_version, prev_event_hash, event_hash" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "SPRING_SECURITY",
                oldTimestamp,
                "user",
                "subject",
                "SUCCESS",
                "KEYCLOAK",
                "127.0.0.1",
                "agent",
                correlationId,
                "GENERATED",
                "HTTP",
                "SUCCESS",
                "fp-" + UUID.randomUUID(),
                1,
                "prevHash",
                "hash"
        );
    }

    @TestConfiguration
    static class MockExportConfig {

        @Bean
        @Primary
        SealedJsonlAuditExportFileService exportFileService() {
            return Mockito.mock(SealedJsonlAuditExportFileService.class);
        }
    }
}