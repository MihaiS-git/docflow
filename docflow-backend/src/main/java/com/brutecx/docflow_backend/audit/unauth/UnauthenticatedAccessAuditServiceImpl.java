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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String httpMethod,
            String path,
            String eventFingerprint
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

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
        ExecutionContext executionContext = ExecutionContext.HTTP;
        AuditResult auditResult = AuditResult.FAILED;

        UnauthenticatedAccessCanonicalMaterialBuilder.Input canonicalInput =
                new UnauthenticatedAccessCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        correlationId,
                        correlationSource.name(),
                        executionContext.name(),
                        auditResult.name(),
                        resolvedMethod,
                        resolvedPath,
                        ctx.ip(),
                        ctx.userAgent(),
                        fingerprint
                );

        String canonicalMaterial =
                canonicalBuilder.buildCanonicalMaterial(canonicalInput);

        AuditPartition partition = AuditPartition.global(STREAM);

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
                    executionContext,
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
        } catch (DataIntegrityViolationException ignored) {
            log.debug(
                    "UNAUTH_AUDIT_DEDUP correlationId={} method={} path={}",
                    correlationId,
                    resolvedMethod,
                    resolvedPath
            );
        } catch (Exception ex) {
            metrics.increment(
                    STREAM,
                    ExecutionContext.HTTP.name(),
                    ex
            );
            log.error(
                    "UNAUTH_AUDIT_WRITE_FAILED correlationId={} method={} path={}",
                    correlationId,
                    resolvedMethod,
                    resolvedPath,
                    ex
            );
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("UnauthenticatedAccessAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for UnauthenticatedAccess audit");
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}
