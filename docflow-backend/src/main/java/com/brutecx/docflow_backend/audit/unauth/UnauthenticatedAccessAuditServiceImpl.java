package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.logging.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();
    private static final String PARTITION = "GLOBAL";

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String httpMethod, String path) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        if (correlationId == null) {
            SecurityAuditLogger.auditFailure(
                    "unauthenticated_access_audit_skipped_missing_correlation",
                    STREAM,
                    PARTITION,
                    null,
                    null,
                    null
            );
            return;
        }

        String resolvedMethod = normalizeOr(httpMethod, "UNKNOWN");
        String resolvedPath = normalizeOr(path, "UNKNOWN");
        Instant eventTimestamp = Instant.now();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM, resolvedMethod, resolvedPath, correlationId
        ));

        CorrelationSource correlationSource = CorrelationSource.REQUEST_ID;

        UnauthenticatedAccessCanonicalMaterialBuilder.Input canonicalInput =
                new UnauthenticatedAccessCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        AuditResult.FAILED.name(),
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(canonicalInput);
        AuditPartition partition = partitionResolver.unauthenticatedAccess();

        try {
            AuditStreamExecutor.WriteOutcome outcome = executor.execute(
                    STREAM,
                    EXEC_CTX,
                    partition,
                    canonicalMaterial,
                    repository,
                    prepared -> new UnauthenticatedAccessAuditEvent(
                            eventTimestamp,
                            correlationId,
                            correlationSource,
                            ExecutionContext.HTTP,
                            AuditResult.FAILED,
                            resolvedMethod,
                            resolvedPath,
                            ctx.ip(),
                            ctx.userAgent(),
                            fingerprint,
                            prepared.chainVersion(),
                            prepared.prevHash(),
                            prepared.eventHash()
                    )
            );

            if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                log.debug(
                        "security_event",
                        kv("event.category", "audit"),
                        kv("event.action", "unauthenticated_access_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("correlation.id", correlationId),
                        kv("http.method", resolvedMethod),
                        kv("http.path", resolvedPath)
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "unauthenticated_access_audit_record_failed",
                    STREAM,
                    PARTITION,
                    null,
                    ex,
                    correlationId
            );
        }
    }

    private void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException(
                    "UnauthenticatedAccessAudit invoked outside HTTP request context"
            );
        }
    }

    private String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) return null;
        return corr;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}