package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditService {

    private final OnboardingAuditEventRepository repository;
    private final AuditRequestContextExtractor auditRequestContextExtractor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOnce(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId
    ) {
        if (repository.existsByInviteId(inviteId)) {
            return; // HARD guarantee: no double logging
        }

        var ctx = auditRequestContextExtractor.fromCurrentRequest();

        repository.save(new OnboardingAuditEvent(
                actorUserId,
                subjectId,
                tenantId,
                inviteId,
                ctx.requestId(),
                ctx.ip(),
                ctx.userAgent()
        ));
    }
}
