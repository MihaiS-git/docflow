package com.brutecx.docflow_backend.domain.admin;

import com.brutecx.docflow_backend.api.dto.admin.AdminUserResponseDTO;
import com.brutecx.docflow_backend.api.error.SelfActionForbiddenException;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.security.AuthRoleExtractor;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeMetadata;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeReason;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.security.session.SessionRevocationService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final IAdminAuditEventService adminAuditEventService;
    private final UserService userService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuthRoleExtractor authRoleExtractor;
    private final AuditRequestContextExtractor auditRequestContextExtractor;

    public List<AdminUserResponseDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    List<String> rawRoles =
                            keycloakAdminClient.fetchUserRealmRoles(
                                    user.getExternalSubjectId()
                            );

                    List<String> roles =
                            authRoleExtractor.filterRealmRoles(rawRoles);

                    return new AdminUserResponseDTO(
                            user.getId(),
                            user.getEmail(),
                            user.getStatus(),
                            roles
                    );
                })
                .toList();
    }

    @Transactional
    public void lockUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        if (actor.getId().equals(target.getId())) {
            throw new SelfActionForbiddenException("You cannot lock your own account");
        }

        if (target.getStatus() == UserStatus.LOCKED) {
            return; // idempotent
        }
        target.lock();

        int revokedSessions = sessionRevocationService.revokeSessionsBySubject(
                ctx,
                target.getExternalSubjectId(),
                actor.getExternalSubjectId()
        );

        String eventFingerprint = EventFingerprint.of(List.of(
                "ADMIN",
                AdminAuditActionType.USER_LOCKED.name(),
                actor.getId().toString(),
                target.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        try {
            adminAuditEventService.record(
                    actor.getId(),
                    ctx.ip(),
                    ctx.userAgent(),
                    ctx.correlationId(),
                    actor.getExternalSubjectId(),
                    actor.getTenant().getId(),
                    AdminAuditActionType.USER_LOCKED,
                    target.getId(),
                    new UserStateChangeMetadata(
                            UserStateChangeReason.MANUAL_ADMIN_ACTION,
                            "revokedSessions=" + revokedSessions
                    ),
                    eventFingerprint
            );
        } catch (Exception e) {
            log.error(
                    "AUDIT FAILURE for USER_LOCKED actorId={} targetUserId={} tenantId={}",
                    actor.getId(),
                    target.getId(),
                    actor.getTenant().getId(),
                    e
            );
            throw e;
        }
    }

    @Transactional
    public void disableUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        if (actor.getId().equals(target.getId())) {
            throw new SelfActionForbiddenException("You cannot disable your own account");
        }

        if (target.getStatus() == UserStatus.DISABLED) {
            return; // idempotent
        }
        target.disable();

        int revokedSessions = sessionRevocationService.revokeSessionsBySubject(
                ctx,
                target.getExternalSubjectId(),
                actor.getExternalSubjectId()
        );

        String eventFingerprint = EventFingerprint.of(List.of(
                "ADMIN",
                AdminAuditActionType.USER_DISABLED.name(),
                actor.getId().toString(),
                target.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        adminAuditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.correlationId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.USER_DISABLED,
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        "revokedSessions=" + revokedSessions
                ),
                eventFingerprint
        );
    }

    @Transactional
    public void activateUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        if (target.getStatus() == UserStatus.ACTIVE) {
            return; // idempotent
        }
        target.activate();

        String eventFingerprint = EventFingerprint.of(List.of(
                "ADMIN",
                AdminAuditActionType.USER_ACTIVATED.name(),
                actor.getId().toString(),
                target.getId().toString(),
                actor.getTenant().getId().toString(),
                ctx.correlationId()
        ));

        adminAuditEventService.record(
                actor.getId(),
                ctx.ip(),
                ctx.userAgent(),
                ctx.correlationId(),
                actor.getExternalSubjectId(),
                actor.getTenant().getId(),
                AdminAuditActionType.USER_ACTIVATED,
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        null
                ),
                eventFingerprint
        );
    }
}
