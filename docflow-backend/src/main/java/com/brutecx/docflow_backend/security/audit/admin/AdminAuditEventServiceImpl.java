package com.brutecx.docflow_backend.security.audit.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditEventServiceImpl implements IAdminAuditEventService{

    private final AdminAuditEventRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata
    ) {

        AdminAuditEvent event = new AdminAuditEvent(
                actorUserId,
                tenantId,
                actionType,
                targetUserId,
                metadata
        );

        repository.save(event);
    }
}
