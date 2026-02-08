package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.*;
import com.brutecx.docflow_backend.audit.sensitive.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
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
public class CredentialLifecycleAuditQueryService {

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<CredentialLifecycleAuditEvent> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId,
            Pageable pageable
    ) {

        Pageable sorted =
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "timestamp")
                );

        Page<CredentialLifecycleAuditEvent> page;

        if (correlationId != null && !correlationId.isBlank()) {
            page = repository.findByCorrelationId(correlationId, sorted);
        } else if (subjectExternalId != null && !subjectExternalId.isBlank()) {
            page = repository.findBySubjectExternalId(subjectExternalId, sorted);
        } else {
            page = repository.findByTimestampBetween(
                    from != null ? from : Instant.EPOCH,
                    to != null ? to : Instant.now(),
                    sorted
            );
        }

        recordSensitiveAccess();

        return page;
    }

    private void recordSensitiveAccess() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "CREDENTIAL_LIFECYCLE",
                actor.getId().toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "CREDENTIAL_LIFECYCLE",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read credential lifecycle audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
