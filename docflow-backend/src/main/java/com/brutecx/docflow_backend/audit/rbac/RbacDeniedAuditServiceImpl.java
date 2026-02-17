package com.brutecx.docflow_backend.audit.rbac;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditServiceImpl implements IRbacDeniedAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = RbacDeniedCanonicalMaterialBuilder.STREAM;

    private final RbacDeniedAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final RbacDeniedCanonicalMaterialBuilder canonicalBuilder;
    private final AuditChainService auditChainService;
    private final AuditWriteFailureMetrics writeFailureMetrics; // ✅ ADDED

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String subjectId,
            String httpMethod,
            String path,
            String eventFingerprint
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubject = normalizeOr(subjectId, "UNKNOWN");
        String resolvedMethod = normalizeOr(httpMethod, "UNKNOWN");
        String resolvedPath = normalizeOr(path, "UNKNOWN");

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        resolvedSubject,
                        resolvedMethod,
                        resolvedPath,
                        correlationId
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();
        Instant eventTimestamp = Instant.now();

        RbacDeniedCanonicalMaterialBuilder.Input input =
                new RbacDeniedCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource != null ? correlationSource.name() : null,
                        ExecutionContext.HTTP.name(),
                        AuditResult.DENIED.name(),
                        resolvedSubject,
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint
                );

        String canonicalMaterial =
                canonicalBuilder.buildCanonicalMaterial(input);

        AuditPartition partition =
                (!"UNKNOWN".equals(resolvedSubject) && !resolvedSubject.isBlank())
                        ? AuditPartition.subject(STREAM, resolvedSubject.trim())
                        : AuditPartition.global(STREAM);

        AuditChainService.ChainHash chain =
                auditChainService.nextHash(
                        partition,
                        canonicalMaterial
                );

        try {
            repository.save(new RbacDeniedAuditEvent(
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
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (Exception ex) {
            // ✅ METRIC INCREMENT (non-blocking instrumentation)
            writeFailureMetrics.increment(
                    STREAM,
                    ExecutionContext.HTTP.name(),
                    ex
            );
            log.error(
                    "RBAC_DENIED_AUDIT_WRITE_FAILED correlationId={} subjectId={} method={} path={}",
                    correlationId,
                    resolvedSubject,
                    resolvedMethod,
                    resolvedPath,
                    ex
            );
            throw ex;
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

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
