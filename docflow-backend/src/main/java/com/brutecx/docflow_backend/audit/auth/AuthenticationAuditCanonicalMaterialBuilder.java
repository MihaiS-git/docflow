package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical material builder for AUTH stream.
 * MUST be used by both:
 * - AuthenticationEventListener (write path)
 * - AuthenticationAuditQueryService (verify path)
 *
 * Field order is explicit and stable.
 * Do NOT reorder without bumping canonical version.
 */
@Component
public class AuthenticationAuditCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AuthenticationAuditCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "AUTH";

    private final AuditCanonicalVersionProvider versionProvider;

    public AuthenticationAuditCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    public record Input(
            Instant timestamp,
            String username,
            AuthenticationResult result,
            String subjectId,
            String ip,
            String correlationId,
            String fingerprint
    ) {
    }

    public Input fromEvent(AuthenticationEvent event) {
        return new Input(
                event.getTimestamp(),
                event.getUsername(),
                event.getResult(),
                // subjectId not stored in entity → derive from fingerprint partition model
                null,
                event.getIp(),
                event.getCorrelationId(),
                event.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,
                "timestamp=" + in.timestamp().toEpochMilli(),
                "username=" + in.username(),
                "result=" + in.result().name(),
                "ip=" + in.ip(),
                "correlationId=" + in.correlationId(),
                "fingerprint=" + in.fingerprint()
        );
    }
}
