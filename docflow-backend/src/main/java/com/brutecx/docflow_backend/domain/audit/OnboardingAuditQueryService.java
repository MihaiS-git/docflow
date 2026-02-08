package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.admin.audit.OnboardingAuditDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditQueryService {

    private final OnboardingAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<OnboardingAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            Pageable pageable
    ) {

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        Page<OnboardingAuditEvent> page;

        if (correlationId != null && !correlationId.isBlank()) {
            page = repository.findByCorrelationId(correlationId, sortedPageable);
        } else if (subjectId != null && !subjectId.isBlank()) {
            page = repository.findBySubjectId(subjectId, sortedPageable);
        } else if (tenantId != null) {
            page = repository.findByTenantId(tenantId, sortedPageable);
        } else {
            page = repository.findByTimestampBetween(
                    from != null ? from : Instant.EPOCH,
                    to != null ? to : Instant.now(),
                    sortedPageable
            );
        }

        recordSensitiveAccess();

        return page.map(OnboardingAuditDTO::from);
    }

    private void recordSensitiveAccess() {
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "ONBOARDING",
                actor.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "ONBOARDING",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read onboarding audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
