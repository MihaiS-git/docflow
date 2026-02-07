package com.brutecx.docflow_backend.domain.admin;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
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

import java.util.List;
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
        if (NON_ASSIGNABLE_ROLES.contains(roleName)) {
            return; // idempotent no-op
        }

        User actor = userService.getRequiredCurrentUser();
        User target = userService.getRequired(targetUserId);
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

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

        String eventFingerprint = EventFingerprint.of(List.of(
                "ADMIN",
                AdminAuditActionType.ROLE_ASSIGNED.name(),
                actor.getId().toString(),
                target.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        auditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.correlationId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.ROLE_ASSIGNED,
                target.getId(),
                new RoleChangeMetadata(roleName, null),
                eventFingerprint
        );
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

        String eventFingerprint = EventFingerprint.of(List.of(
                "ADMIN",
                AdminAuditActionType.ROLE_REVOKED.name(),
                actor.getId().toString(),
                target.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        auditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.correlationId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.ROLE_REVOKED,
                target.getId(),
                new RoleChangeMetadata(roleName, null),
                eventFingerprint
        );
    }
}
