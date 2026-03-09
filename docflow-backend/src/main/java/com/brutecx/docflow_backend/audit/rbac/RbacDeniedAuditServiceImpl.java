package com.brutecx.docflow_backend.audit.rbac;

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
public class RbacDeniedAuditServiceImpl implements IRbacDeniedAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = RbacDeniedCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();
    private static final String UNKNOWN = "UNKNOWN";

    private final RbacDeniedAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final RbacDeniedCanonicalMaterialBuilder canonicalBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String subjectId,
            String httpMethod,
            String path
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubject = normalize(subjectId);
        String resolvedMethod = normalize(httpMethod);
        String resolvedPath = normalize(stripQuery(path));

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                resolvedSubject,
                resolvedMethod,
                resolvedPath,
                correlationId
        ));

        CorrelationSource correlationSource =
                resolvedSubject.equals(UNKNOWN)
                        ? CorrelationSource.GENERATED
                        : CorrelationSource.REQUEST_ID;

        Instant eventTimestamp = Instant.now();

        RbacDeniedCanonicalMaterialBuilder.Input input =
                new RbacDeniedCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        AuditResult.DENIED.name(),
                        resolvedSubject,
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);

        AuditPartition partition =
                !UNKNOWN.equals(resolvedSubject)
                        ? partitionResolver.rbacDenied(resolvedSubject)
                        : partitionResolver.unauthenticatedAccess();

        try {
            AuditStreamExecutor.WriteOutcome outcome = executor.execute(
                    STREAM,
                    EXEC_CTX,
                    partition,
                    canonicalMaterial,
                    repository,
                    prepared -> new RbacDeniedAuditEvent(
                            eventTimestamp,
                            correlationId,
                            correlationSource,
                            ExecutionContext.HTTP,
                            AuditResult.DENIED,
                            resolvedSubject,
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
                        kv("event.action", "rbac_denied_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("correlation.id", correlationId),
                        kv("subject.id", resolvedSubject),
                        kv("http.method", resolvedMethod),
                        kv("http.path", resolvedPath)
                );
            }

        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "rbac_denied_audit_record_failed",
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
            throw new IllegalStateException("RbacDeniedAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for RBAC denied audit");
        }
        return corr;
    }

    private static String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : UNKNOWN;
    }

    private static String stripQuery(String path) {
        if (path == null) {
            return null;
        }
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }
}