package com.brutecx.docflow_backend.audit.sensitive;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditServiceImpl
        implements ISensitiveAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = "SENSITIVE_ACCESS";

    private final SensitiveAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;

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
            throw new IllegalStateException(
                    "SensitiveAccessAudit requires tenantId (multi-tenant invariant)"
            );
        }

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubjectId =
                (subjectId != null && !subjectId.isBlank())
                        ? subjectId.trim()
                        : "UNKNOWN";

        String resolvedResource =
                (resource != null && !resource.isBlank())
                        ? resource.trim()
                        : "UNKNOWN";

        String resolvedAction =
                (action != null && !action.isBlank())
                        ? action.trim()
                        : "UNKNOWN";

        String resolvedPath =
                (resourcePath != null && !resourcePath.isBlank())
                        ? resourcePath.trim()
                        : "UNKNOWN";

        String resolvedReason =
                (reasonCode != null && !reasonCode.isBlank())
                        ? reasonCode.trim()
                        : "NONE";

        String resolvedClassification =
                dataClassification != null
                        ? dataClassification.name()
                        : "UNSPECIFIED";

        String fingerprint =
                resolveFingerprint(
                        eventFingerprint,
                        STREAM,
                        String.valueOf(actorUserId),
                        tenantId.toString(),
                        String.valueOf(subjectType),
                        resolvedSubjectId,
                        resolvedResource,
                        resolvedAction,
                        resolvedPath,
                        resolvedReason,
                        resolvedClassification,
                        correlationId
                );

        CorrelationSource correlationSource = resolveCorrelationSource();

        String material = String.join("|",
                STREAM,
                String.valueOf(actorUserId),
                String.valueOf(actorExternalSubjectId),
                tenantId.toString(),
                String.valueOf(subjectType),
                resolvedSubjectId,
                resolvedResource,
                resolvedAction,
                resolvedPath,
                correlationId,
                resolvedReason,
                resolvedClassification,
                fingerprint
        );

        try {

            /*
             * Strict per-tenant hash partition.
             */
            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            STREAM,
                            tenantId.toString(),
                            material
                    );

            repository.saveAndFlush(new SensitiveAccessAuditEvent(
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
                    dataClassification,
                    fingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "SENSITIVE ACCESS AUDIT DEDUPLICATED correlationId={} fingerprint={}",
                    correlationId,
                    fingerprint
            );
        } catch (Exception ex) {
            log.error(
                    "SENSITIVE ACCESS AUDIT FAILURE correlationId={} subjectType={} subjectId={} resource={} action={}",
                    correlationId,
                    subjectType,
                    resolvedSubjectId,
                    resolvedResource,
                    resolvedAction,
                    ex
            );
            throw ex;
        }
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException(
                    "SensitiveAccessAudit invoked outside HTTP request context"
            );
        }
    }

    private String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException(
                    "Missing correlationId for SensitiveAccess audit"
            );
        }
        return corr;
    }

    private CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private String resolveFingerprint(String provided, String... parts) {
        return (provided != null && !provided.isBlank())
                ? provided
                : EventFingerprint.of(List.of(parts));
    }
}
