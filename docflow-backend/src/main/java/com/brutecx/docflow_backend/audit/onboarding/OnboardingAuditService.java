package com.brutecx.docflow_backend.audit.onboarding;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final OnboardingAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;

    /* =========================================================
       SUCCESS
       ========================================================= */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String eventFingerprint
    ) {
        ensureHttpContext();
        requireNonNull(inviteId, "inviteId");
        requireNonNull(tenantId, "tenantId");

        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalStateException("Onboarding SUCCESS requires subjectId");
        }

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String fingerprint = resolveFingerprint(
                eventFingerprint,
                OnboardingCanonicalMaterialBuilder.STREAM,
                "SUCCESS",
                inviteId.toString(),
                subjectId,
                tenantId.toString(),
                correlationId
        );

        CorrelationSource correlationSource = resolveCorrelationSource();

        OnboardingCanonicalMaterialBuilder.Input input =
                new OnboardingCanonicalMaterialBuilder.Input(
                        Instant.now(),
                        actorUserId,
                        subjectId,
                        tenantId,
                        inviteId,
                        correlationId,
                        correlationSource.name(),
                        ExecutionContext.HTTP.name(),
                        ctx.ip(),
                        ctx.userAgent(),
                        AuditResult.SUCCESS.name(),
                        OnboardingOutcome.SUCCESS,
                        "ONBOARDING_SUCCESS",
                        null,
                        fingerprint
                );


        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);

        try {
            AuditPartition partition =
                    AuditPartition.tenant(
                            canonicalBuilder.stream(),
                            tenantId.toString()
                    );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    subjectId,
                    tenantId,
                    inviteId,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    ctx.ip(),
                    ctx.userAgent(),
                    AuditResult.SUCCESS,
                    OnboardingOutcome.SUCCESS,
                    "ONBOARDING_SUCCESS",
                    null,
                    fingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

        } catch (Exception ex) {
            log.error(
                    "ONBOARDING AUDIT FAILURE (SUCCESS) correlationId={} inviteId={} subjectId={}",
                    correlationId,
                    inviteId,
                    subjectId,
                    ex
            );
            throw ex;
        }
    }

    /* =========================================================
       FAILURE
       ========================================================= */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID actorUserId,
            UUID tenantId,
            UUID inviteId,
            String failureReason,
            String eventFingerprint
    ) {
        ensureHttpContext();
        requireNonNull(inviteId, "inviteId");
        requireNonNull(tenantId, "tenantId");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedReason =
                (failureReason != null && !failureReason.isBlank())
                        ? failureReason
                        : "-";

        String fingerprint = resolveFingerprint(
                eventFingerprint,
                OnboardingCanonicalMaterialBuilder.STREAM,
                "FAILURE",
                inviteId.toString(),
                tenantId.toString(),
                resolvedReason,
                correlationId
        );

        CorrelationSource correlationSource = resolveCorrelationSource();

        OnboardingCanonicalMaterialBuilder.Input input =
                new OnboardingCanonicalMaterialBuilder.Input(
                        Instant.now(),
                        actorUserId,
                        null,
                        tenantId,
                        inviteId,
                        correlationId,
                        correlationSource.name(),
                        ExecutionContext.HTTP.name(),
                        ctx.ip(),
                        ctx.userAgent(),
                        AuditResult.SUCCESS.name(),
                        OnboardingOutcome.SUCCESS,
                        "ONBOARDING_SUCCESS",
                        null,
                        fingerprint
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);

        try {
            AuditPartition partition =
                    AuditPartition.tenant(
                            canonicalBuilder.stream(),
                            tenantId.toString()
                    );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    null,
                    tenantId,
                    inviteId,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    ctx.ip(),
                    ctx.userAgent(),
                    AuditResult.FAILED,
                    OnboardingOutcome.FAILURE,
                    "ONBOARDING_FAILURE",
                    resolvedReason,
                    fingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

        } catch (Exception ex) {
            log.error(
                    "ONBOARDING AUDIT FAILURE (FAILURE) correlationId={} inviteId={} reason={}",
                    correlationId,
                    inviteId,
                    resolvedReason,
                    ex
            );
            throw ex;
        }
    }

    /* =========================================================
       INTERNALS
       ========================================================= */

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("OnboardingAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for Onboarding audit");
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String resolveFingerprint(String provided, String... parts) {
        return (provided != null && !provided.isBlank())
                ? provided
                : EventFingerprint.of(List.of(parts));
    }

    private static void requireNonNull(Object v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
