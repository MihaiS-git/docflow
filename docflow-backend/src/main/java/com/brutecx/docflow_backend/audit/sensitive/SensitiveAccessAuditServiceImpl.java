package com.brutecx.docflow_backend.audit.sensitive;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
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
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditServiceImpl implements ISensitiveAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = SensitiveAccessCanonicalMaterialBuilder.STREAM;

    private final SensitiveAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final SensitiveAccessCanonicalMaterialBuilder canonicalMaterialBuilder;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId,
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String ignoredCorrelationId,
            String ignoredIp,
            String ignoredUserAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
    ) {

        ensureHttpContext();

        if (tenantId == null) {
            throw new IllegalStateException("SensitiveAccessAudit requires tenantId");
        }
        Objects.requireNonNull(subjectType, "SensitiveAccessAudit requires subjectType");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubjectId = normalizeOr(subjectId, "UNKNOWN");
        String resolvedResource = normalizeOr(resource, "UNKNOWN");
        String resolvedAction = normalizeOr(action, "UNKNOWN");
        String resolvedPath = normalizeOr(resourcePath, "UNKNOWN");
        String resolvedReason = normalizeOr(reasonCode, "NONE");

        SensitiveDataClassification classification =
                (dataClassification != null)
                        ? dataClassification
                        : SensitiveDataClassification.INTERNAL;

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        String.valueOf(actorUserId),
                        tenantId.toString(),
                        subjectType.name(),
                        resolvedSubjectId,
                        resolvedResource,
                        resolvedAction,
                        resolvedPath,
                        resolvedReason,
                        classification.name(),
                        correlationId
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();

        // One timestamp used consistently (canonical + persisted row)
        Instant now = Instant.now();

        // 1) Build canonical builder Input directly (no extra canonical-input type, no fromInput needed)
        SensitiveAccessCanonicalMaterialBuilder.Input canonicalInput =
                new SensitiveAccessCanonicalMaterialBuilder.Input(
                        now,

                        actorUserId,
                        actorExternalSubjectId,
                        tenantId,

                        subjectType,
                        resolvedSubjectId,
                        resolvedResource,
                        resolvedAction,
                        resolvedPath,

                        correlationId,
                        (correlationSource != null ? correlationSource.name() : null),
                        ExecutionContext.HTTP.name(),
                        AuditResult.SUCCESS.name(),
                        ctx.ip(),
                        ctx.userAgent(),

                        resolvedReason,
                        reasonDetail,
                        classification,

                        fingerprint
                );

        String canonicalMaterial =
                canonicalMaterialBuilder.buildCanonicalMaterial(canonicalInput);

        try {

            AuditPartition partition =
                    AuditPartition.tenant(STREAM, tenantId.toString());

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            SensitiveAccessAuditEvent entity =
                    new SensitiveAccessAuditEvent(
                            now,

                            actorUserId,
                            actorExternalSubjectId,
                            tenantId,

                            subjectType,
                            resolvedSubjectId,
                            resolvedResource,
                            resolvedAction,
                            resolvedPath,

                            correlationId,
                            correlationSource,
                            ExecutionContext.HTTP,
                            AuditResult.SUCCESS,
                            ctx.ip(),
                            ctx.userAgent(),

                            resolvedReason,
                            reasonDetail,
                            classification,

                            fingerprint,

                            chain.chainVersion(),
                            chain.prevHash(),
                            chain.eventHash()
                    );

            repository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "SensitiveAccessAudit deduplicated correlationId={} fingerprint={}",
                    correlationId,
                    fingerprint
            );
        } catch (Exception ex) {
            log.error(
                    "SensitiveAccessAudit failure correlationId={}",
                    correlationId,
                    ex
            );
            throw ex;
        }
    }

    private void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("SensitiveAccessAudit invoked outside HTTP request context");
        }
    }

    private String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId");
        }
        return corr;
    }

    private CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}
