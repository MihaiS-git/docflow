package com.brutecx.docflow_backend.audit.keyrotation;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class AuditExportSigningKeyRotationAuditServiceImpl
        implements AuditExportSigningKeyRotationAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM =
            AuditExportSigningKeyRotationCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final AuditExportSigningKeyRotationEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuditPartitionResolver partitionResolver;
    private final AuditExportSigningKeyRotationCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditCanonicalVersionProvider canonicalVersionProvider;
    private final CanonicalJsonService canonicalJsonService;
    private final AuditWriteFailureMetrics metrics;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRotation(
            AuditExportSigningKeyRotationReason reason,
            String oldKeyId,
            String newKeyId,
            String newFingerprintSha256Hex
    ) {

        ensureHttpContext();

        if (reason == null) throw new IllegalArgumentException("reason is required");
        if (newKeyId == null || newKeyId.isBlank())
            throw new IllegalArgumentException("newKeyId is required");
        if (newFingerprintSha256Hex == null || newFingerprintSha256Hex.isBlank())
            throw new IllegalArgumentException("newFingerprintSha256Hex is required");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);
        CorrelationSource correlationSource = resolveCorrelationSource();

        Instant ts = Instant.now();

        AuditExportSigningKeyRotationMetadata metadata =
                new AuditExportSigningKeyRotationMetadata(
                        reason,
                        oldKeyId,
                        newKeyId,
                        newFingerprintSha256Hex
                );

        String fingerprint = buildFingerprint(ts, correlationId, metadata);

        String canonicalMaterial =
                canonicalMaterialBuilder.buildCanonicalMaterial(
                        new AuditExportSigningKeyRotationCanonicalMaterialBuilder.Input(
                                ts,
                                correlationId,
                                metadata,
                                fingerprint
                        )
                );

        AuditPartition partition =
                partitionResolver.auditExportSigningKeyRotation();

        final long startNs = System.nanoTime();

        try {

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(partition, canonicalMaterial);

            repository.save(new AuditExportSigningKeyRotationEvent(
                    ts,
                    correlationId,
                    metadata,
                    fingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

            metrics.incrementSuccess(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));

        } catch (DataIntegrityViolationException ignored) {

            metrics.incrementDedup(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));

        } catch (Exception ex) {

            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "audit_export_key_rotation_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", correlationSource.name()),
                    kv("keyrotation.reason", reason.name()),
                    kv("keyrotation.old_key_id", oldKeyId),
                    kv("keyrotation.new_key_id", newKeyId),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );

            throw ex;
        }
    }

    private String buildFingerprint(
            Instant ts,
            String correlationId,
            AuditExportSigningKeyRotationMetadata metadata
    ) {
        int cv = canonicalVersionProvider.canonicalVersion();

        List<String> fp = new ArrayList<>();
        fp.add(STREAM);
        fp.add("CV=" + cv);
        fp.add("ts=" + ts.toEpochMilli());
        fp.add("correlationId=" + correlationId);
        fp.add("metadata=" + canonicalJsonService.toCanonicalJson(metadata));

        return EventFingerprint.of(fp);
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException(
                    "Key rotation audit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException(
                    "Missing correlationId for key rotation audit event");
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(
                MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }
}