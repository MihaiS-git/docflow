package com.brutecx.docflow_backend.domain.admin;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.RoleChangeMetadata;
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
    private final AuditRequestContextExtractor auditRequestContextExtractor;

    private static final Set<String> NON_ASSIGNABLE_ROLES = Set.of("USER");

    @Transactional
    public void assignRole(
            UUID targetUserId,
            String roleName
    ) {
        log.info("Assigning role {} to user {}", roleName, targetUserId);
        if (NON_ASSIGNABLE_ROLES.contains(roleName)) {
            return; // idempotent no-op
        }

        User actor = userService.getRequiredCurrentUser();
        User target = userService.getRequired(targetUserId);
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        log.info("Assigning role {} to target {}, by actor {}", roleName, target, actor);

        try {
            keycloakRoleAdminClient.assignRealmRole(
                    target.getExternalSubjectId(),
                    roleName
            );
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}: {}", roleName, targetUserId, e.getMessage());
            throw e;
        }

        log.info("Assigned role {} to target {}, by actor {}", roleName, target, actor);

        auditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.requestId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.ROLE_ASSIGNED,
                target.getId(),
                new RoleChangeMetadata(roleName, null)
        );

        log.info("Audit event persisted");
    }

    @Transactional
    public void revokeRole(
            UUID targetUserId,
            String roleName
    ) {
        if (NON_ASSIGNABLE_ROLES.contains(roleName)) {
            return; // idempotent no-op
        }

        User actor = userService.getRequiredCurrentUser();
        User target = userService.getRequired(targetUserId);
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        keycloakRoleAdminClient.revokeRealmRole(
                target.getExternalSubjectId(),
                roleName
        );

        auditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.requestId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.ROLE_REVOKED,
                target.getId(),
                new RoleChangeMetadata(roleName, null)
        );
    }
}
