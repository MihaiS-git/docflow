package com.brutecx.docflow_backend.domain.admin;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.RoleChangeMetadata;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleAdminService {

    private final KeycloakAdminClient keycloakRoleAdminClient;
    private final IAdminAuditEventService auditEventService;
    private final UserService userService;
    private final TenantService tenantService;

    private static final Set<String> NON_ASSIGNABLE_ROLES = Set.of("USER");

    @Transactional
    public void assignRole(
            UUID targetUserId,
            String roleName
    ) {
        if (NON_ASSIGNABLE_ROLES.contains(roleName)) {
            return;
        }

        User actor = userService.getRequiredCurrentUser();
        User target = userService.getRequired(targetUserId);

        try {
            keycloakRoleAdminClient.assignRealmRole(
                    target.getExternalSubjectId(),
                    roleName
            );
        } catch (Exception e) {
            log.error(
                    "ROLE ASSIGN FAILED role={} targetUserId={} actorUserId={}",
                    roleName, targetUserId, actor.getId(), e
            );
            throw e;
        }

        auditEventService.record(
                AdminAuditActionType.ROLE_ASSIGNED,
                tenantService.getRootTenant().getId(),
                actor.getExternalSubjectId(),
                target.getId(),
                new RoleChangeMetadata(roleName, null)
        );
    }

    @Transactional
    public void revokeRole(
            UUID targetUserId,
            String roleName
    ) {
        if (NON_ASSIGNABLE_ROLES.contains(roleName)) {
            return;
        }

        User actor = userService.getRequiredCurrentUser();
        User target = userService.getRequired(targetUserId);

        try {
            keycloakRoleAdminClient.revokeRealmRole(
                    target.getExternalSubjectId(),
                    roleName
            );
        } catch (Exception e) {
            log.error(
                    "ROLE REVOKE FAILED role={} targetUserId={} actorUserId={}",
                    roleName, targetUserId, actor.getId(), e
            );
            throw e;
        }

        auditEventService.record(
                AdminAuditActionType.ROLE_REVOKED,
                tenantService.getRootTenant().getId(),
                actor.getExternalSubjectId(),
                target.getId(),
                new RoleChangeMetadata(roleName, null)
        );
    }
}
