package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditService {

    private final OnboardingAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOnce(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String correlationId,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {
        if (repository.existsByInviteId(inviteId)) {
            return; // HARD guarantee: no double logging
        }

        if (subjectId == null) {
            throw new IllegalStateException(
                    "Onboarding SUCCESS requires a subjectId (post-auth)"
            );
        }
        try {
            CorrelationSource correlationSource =
                    "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;

            String material = String.join("|",
                    "ONBOARDING",
                    "SUCCESS",
                    inviteId.toString(),
                    subjectId,
                    tenantId.toString(),
                    correlationId
            );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            "ONBOARDING",
                            correlationId,
                            material
                    );

            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    subjectId,
                    tenantId,
                    inviteId,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    ip,
                    userAgent,
                    AuditResult.SUCCESS,
                    OnboardingOutcome.SUCCESS,
                    "ONBOARDING_SUCCESS",
                    null,
                    eventFingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (Exception ex) {
            log.error(
                    "ONBOARDING AUDIT FAILURE (SUCCESS). correlationId={} inviteId={} subjectId={}",
                    correlationId, inviteId, subjectId, ex
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String failureReason,
            String correlationId,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {
        if (subjectId != null) {
            throw new IllegalStateException(
                    "Onboarding FAILURE must not have a subjectId (pre-auth)"
            );
        }
        try {
            CorrelationSource correlationSource =
                    "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;

            String material = String.join("|",
                    "ONBOARDING",
                    "FAILURE",
                    inviteId.toString(),
                    tenantId.toString(),
                    failureReason != null ? failureReason : "-"
            );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            "ONBOARDING",
                            correlationId,
                            material
                    );

            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    null,
                    tenantId,
                    inviteId,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    ip,
                    userAgent,
                    AuditResult.FAILED,
                    OnboardingOutcome.FAILURE,
                    "ONBOARDING_FAILURE",
                    failureReason,
                    eventFingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (Exception ex) {
            log.error(
                    "ONBOARDING AUDIT FAILURE (FAILURE). correlationId={} inviteId={} reason={}",
                    correlationId, inviteId, failureReason, ex
            );
        }
    }
}
