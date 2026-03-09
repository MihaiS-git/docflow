package com.brutecx.docflow_backend.audit.lifecycle;

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
public class LifecycleDeniedAuditServiceImpl implements ILifecycleDeniedAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = LifecycleDeniedCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final LifecycleDeniedAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final LifecycleDeniedCanonicalMaterialBuilder canonicalBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            LifecycleAuditMetadata metadata
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubject = normalize(subjectId);
        String resolvedReason = normalize(reasonCode);
        String resolvedMethod = normalize(httpMethod);
        String resolvedPath = normalize(path);

        Instant eventTimestamp = Instant.now();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                resolvedSubject,
                resolvedReason,
                resolvedMethod,
                resolvedPath,
                correlationId
        ));

        CorrelationSource correlationSource =
                ctx.correlationId() != null ? CorrelationSource.REQUEST_ID : CorrelationSource.GENERATED;

        LifecycleDeniedCanonicalMaterialBuilder.Input input =
                new LifecycleDeniedCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        AuditResult.DENIED.name(),
                        resolvedSubject,
                        resolvedReason,
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint,
                        metadata
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);

        AuditPartition partition =
                !"UNKNOWN".equals(resolvedSubject) ? partitionResolver.lifecycleDenied(resolvedSubject)
                        : partitionResolver.unauthenticatedAccess();

        try {
            AuditStreamExecutor.WriteOutcome outcome = executor.execute(
                    STREAM,
                    EXEC_CTX,
                    partition,
                    canonicalMaterial,
                    repository,
                    prepared -> new LifecycleDeniedAuditEvent(
                            eventTimestamp,
                            correlationId,
                            correlationSource,
                            ExecutionContext.HTTP,
                            AuditResult.DENIED,
                            resolvedSubject,
                            resolvedReason,
                            resolvedMethod,
                            resolvedPath,
                            ctx.ip(),
                            ctx.userAgent(),
                            metadata,
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
                        kv("event.action", "lifecycle_denied_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("subject.id", resolvedSubject),
                        kv("lifecycle.reason_code", resolvedReason),
                        kv("http.method", resolvedMethod),
                        kv("http.path", resolvedPath)
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "lifecycle_denied_audit_record_failed",
                    STREAM,
                    !"UNKNOWN".equals(resolvedSubject) ? "SUBJECT" : "GLOBAL",
                    null,
                    ex,
                    correlationId
            );
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException(
                    "LifecycleDeniedAudit invoked outside HTTP request context"
            );
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException(
                    "Missing correlationId for LifecycleDenied audit"
            );
        }
        return corr;
    }

    private static String normalize(String v) {
        return (v != null && !v.isBlank()) ? v.trim() : "UNKNOWN";
    }
}