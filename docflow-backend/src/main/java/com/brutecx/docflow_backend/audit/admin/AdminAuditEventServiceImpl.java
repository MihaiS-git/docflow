package com.brutecx.docflow_backend.audit.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            repository.save(new AdminAuditEvent(
                    actorUserId,
                    ip,
                    userAgent,
                    correlationId,
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

}
