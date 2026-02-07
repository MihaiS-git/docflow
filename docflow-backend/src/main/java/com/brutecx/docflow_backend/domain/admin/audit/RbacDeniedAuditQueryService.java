package com.brutecx.docflow_backend.domain.admin.audit;

import com.brutecx.docflow_backend.api.dto.admin.audit.RbacDeniedAuditDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEventRepository;
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

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditQueryService {

    private final RbacDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<RbacDeniedAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Pageable pageable
    ) {

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        Page<RbacDeniedAuditEvent> page;

        if (correlationId != null && !correlationId.isBlank()) {
            page = repository.findByCorrelationId(correlationId, sortedPageable);
        } else if (subjectId != null && !subjectId.isBlank()) {
            page = repository.findBySubjectId(subjectId, sortedPageable);
        } else {
            page = repository.findByTimestampBetween(
                    from != null ? from : Instant.EPOCH,
                    to != null ? to : Instant.now(),
                    sortedPageable
            );
        }

        recordSensitiveAccess();

        return page.map(RbacDeniedAuditDTO::from);
    }

    private void recordSensitiveAccess() {
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "RBAC_DENIED",
                actor.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "RBAC_DENIED",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read RBAC denied audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
