package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
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
public class AdminAuditEventServiceImpl implements IAdminAuditEventService {

    private final AdminAuditEventRepository repository;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId,
            String ip,
            String userAgent,
            String correlationId,
            String subjectId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata,
            String eventFingerprint
    ) {
        try {
            CorrelationSource correlationSource =
                    "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;

            AuditResult result = resolveResult(metadata);

            repository.save(new AdminAuditEvent(
                    actorUserId,
                    ip,
                    userAgent,
                    correlationId,
                    correlationSource,
                    ExecutionContext.HTTP,
                    result,
                    subjectId,
                    tenantId,
                    actionType,
                    targetUserId,
                    metadata,
                    eventFingerprint
            ));
        } catch (Exception ex) {
            log.error(
                    "ADMIN AUDIT FAILURE. correlationId={} actorUserId={} action={}",
                    correlationId, actorUserId, actionType, ex
            );
        }
    }

    private AuditResult resolveResult(AdminAuditMetadata metadata) {
        if (metadata instanceof InviteAuditMetadata invite) {
            return invite.outcome() == InviteOutcome.FAILURE
                    ? AuditResult.FAILED
                    : AuditResult.SUCCESS;
        }
        // All other admin actions are successful if persisted
        return AuditResult.SUCCESS;
    }

}
