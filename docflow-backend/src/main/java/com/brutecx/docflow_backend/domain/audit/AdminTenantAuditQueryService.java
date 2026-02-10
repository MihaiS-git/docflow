package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTenantAuditQueryService {

    private final AdminAuditEventRepository repository;

    private static final EnumSet<AdminAuditActionType> TENANT_ACTIONS =
            EnumSet.of(
                    AdminAuditActionType.TENANT_CREATED,
                    AdminAuditActionType.TENANT_UPDATED,
                    AdminAuditActionType.TENANT_SUSPENDED,
                    AdminAuditActionType.TENANT_MUTATION_DENIED
            );

    @Transactional(readOnly = true)
    public Page<AdminAuditEvent> findTenantAuditEvents(
            UUID tenantId,
            Pageable pageable
    ) {
        return repository.findByTenantIdAndActionTypeIn(
                tenantId,
                TENANT_ACTIONS,
                pageable
        );
    }
}
