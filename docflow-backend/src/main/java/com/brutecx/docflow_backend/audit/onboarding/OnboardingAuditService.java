package com.brutecx.docflow_backend.audit.onboarding;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditService {

    private final OnboardingAuditEventRepository repository;
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
            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    subjectId,
                    tenantId,
                    inviteId,
                    correlationId,
                    ip,
                    userAgent,
                    OnboardingOutcome.SUCCESS,
                    null,
                    eventFingerprint
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
            repository.save(new OnboardingAuditEvent(
                    actorUserId,
                    null,
                    tenantId,
                    inviteId,
                    correlationId,
                    ip,
                    userAgent,
                    OnboardingOutcome.FAILURE,
                    failureReason,
                    eventFingerprint
            ));
        } catch (Exception ex) {
            log.error(
                    "ONBOARDING AUDIT FAILURE (FAILURE). correlationId={} inviteId={} reason={}",
                    correlationId, inviteId, failureReason, ex
            );
        }
    }

}
