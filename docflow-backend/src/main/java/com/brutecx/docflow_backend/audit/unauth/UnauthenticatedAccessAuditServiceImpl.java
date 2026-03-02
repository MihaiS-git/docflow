package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
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
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String httpMethod,
            String path,
            String eventFingerprint
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        final long startNs = System.nanoTime();

        String correlationId = requireCorrelation(ctx);
        if (correlationId == null) {
            Exception ex = new IllegalStateException("Missing correlationId for UnauthenticatedAccess audit");

            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "unauthenticated_access_audit_skipped_missing_correlation"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "GLOBAL"),
                    kv("correlation.missing", true),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
            return;
        }

        String resolvedMethod = normalizeOr(httpMethod, "UNKNOWN");
        String resolvedPath = normalizeOr(path, "UNKNOWN");

        Instant eventTimestamp = Instant.now();

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        resolvedMethod,
                        resolvedPath,
                        correlationId,
                        String.valueOf(eventTimestamp.toEpochMilli())
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();
        AuditResult auditResult = AuditResult.FAILED;

        UnauthenticatedAccessCanonicalMaterialBuilder.Input canonicalInput =
                new UnauthenticatedAccessCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        auditResult.name(),
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint
                );

        String canonicalMaterial =
                canonicalBuilder.buildCanonicalMaterial(canonicalInput);

        AuditPartition partition =
                partitionResolver.unauthenticatedAccess();

        try {

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            repository.saveAndFlush(new UnauthenticatedAccessAuditEvent(
                    eventTimestamp,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    auditResult,
                    resolvedMethod,
                    resolvedPath,
                    ctx.ip(),
                    ctx.userAgent(),
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
                    kv("event.action", "unauthenticated_access_audit_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "GLOBAL"),
                    kv("audit.result", auditResult.name()),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", correlationSource.name()),
                    kv("http.method", resolvedMethod),
                    kv("http.path", resolvedPath),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("UnauthenticatedAccessAudit invoked outside HTTP request context");
        }
    }

    /**
     * IMPORTANT: Do NOT throw here. This may run from Security EntryPoint / access control flow.
     * If correlation is missing, we fail safely by skipping persistence and recording failure telemetry.
     */
    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            return null;
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(
                MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}