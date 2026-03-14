package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public final class AuthenticationAuditCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AuthenticationAuditCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "AUTHENTICATION";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;
    private final ObjectMapper objectMapper;

    public AuthenticationAuditCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider,
            ObjectMapper objectMapper
    ) {
        this.versionProvider = versionProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    public record Input(
            Instant timestamp,
            AuthenticationEventSource source,
            String username,
            String subjectId,
            AuthenticationResult result,
            AuthenticationAuditMetadata metadata,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            String correlationSource,
            String executionContext,
            String auditResult,
            String fingerprint
    ) {}

    public Input fromEvent(AuthenticationEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        return new Input(
                event.getTimestamp(),
                event.getSource(),
                event.getUsername(),
                event.getSubjectId(),
                event.getAuthenticationResult(),
                event.getMetadata(), // <-- FIXED
                event.getIdp(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getCorrelationSource() != null ? event.getCorrelationSource().name() : null,
                event.getExecutionContext() != null ? event.getExecutionContext().name() : null,
                event.getResult() != null ? event.getResult().name() : null,
                event.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.timestamp(), "timestamp must not be null");
        Objects.requireNonNull(in.result(), "result must not be null");
        Objects.requireNonNull(in.source(), "source must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,

                "timestamp=" + in.timestamp().toEpochMilli(),
                "source=" + in.source().name(),
                "username=" + normalize(in.username()),
                "subjectId=" + normalize(in.subjectId()),
                "result=" + in.result().name(),
                "metadata=" + normalize(toDeterministicJson(in.metadata())), // <-- FIXED
                "idp=" + normalize(in.idp()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "auditResult=" + normalize(in.auditResult()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String toDeterministicJson(AuthenticationAuditMetadata metadata) {
        if (metadata == null) return null;
        try {
            ObjectMapper m = objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return m.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize AUTH metadata", ex);
        }
    }

    private static String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }
}
