package com.brutecx.docflow_backend.audit.lifecycle;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LifecycleDeniedAuditServiceImpl implements ILifecycleDeniedAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = "LIFECYCLE_DENIED";

    private final LifecycleDeniedAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String eventFingerprint
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubject = normalizeOr(subjectId, "UNKNOWN");
        String resolvedReason = normalizeOr(reasonCode, "UNKNOWN");
        String resolvedMethod = normalizeOr(httpMethod, "UNKNOWN");
        String resolvedPath = normalizeOr(path, "UNKNOWN");

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        resolvedSubject,
                        resolvedReason,
                        resolvedMethod,
                        resolvedPath,
                        correlationId
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();

        try {
            repository.save(new LifecycleDeniedAuditEvent(
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
                    fingerprint
            ));
        } catch (Exception ex) {
            log.error(
                    "LIFECYCLE AUDIT FAILURE correlationId={} subjectId={} reasonCode={} method={} path={}",
                    correlationId,
                    resolvedSubject,
                    resolvedReason,
                    resolvedMethod,
                    resolvedPath,
                    ex
            );
            throw ex;
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("LifecycleDeniedAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for LifecycleDenied audit");
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
