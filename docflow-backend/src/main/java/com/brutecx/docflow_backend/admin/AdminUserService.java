package com.brutecx.docflow_backend.admin;

import com.brutecx.docflow_backend.security.AuthRoleExtractor;
import com.brutecx.docflow_backend.security.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.security.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.security.audit.admin.UserStateChangeMetadata;
import com.brutecx.docflow_backend.security.audit.admin.UserStateChangeReason;
import com.brutecx.docflow_backend.security.audit.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.security.session.SessionRevocationService;
import com.brutecx.docflow_backend.user.User;
import com.brutecx.docflow_backend.user.UserRepository;
import com.brutecx.docflow_backend.user.UserService;
import com.brutecx.docflow_backend.user.UserStatus;
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
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final IAdminAuditEventService adminAuditEventService;
    private final UserService userService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuthRoleExtractor authRoleExtractor;

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ADMIN", "AUDITOR", "REVIEWER", "USER");

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
        log.info("Target user {}", target);
        User actor = userService.getRequiredCurrentUser();
        log.info("Actor user {}", actor);

        if (target.getStatus() == UserStatus.LOCKED) {
            log.info("User {} is already locked", target);
            return; // idempotent
        }
        target.lock();
        userRepository.flush();

        log.info("User {} locked", target);

        sessionRevocationService.revokeSessionsBySubject(
                target.getExternalSubjectId()
        );
        log.info("Sessions revoked for user {}", target);

        try {
            adminAuditEventService.record(
                    actor.getId(),
                    actor.getTenant().getId(),
                    AdminAuditActionType.USER_LOCKED,
                    target.getId(),
                    new UserStateChangeMetadata(
                            UserStateChangeReason.MANUAL_ADMIN_ACTION,
                            null
                    )
            );
            log.info("Audit record peristed.");

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

        if (target.getStatus() == UserStatus.DISABLED) {
            return; // idempotent
        }
        target.disable();
        sessionRevocationService.revokeSessionsBySubject(
                target.getExternalSubjectId()
        );

        adminAuditEventService.record(
                actor.getId(),
                actor.getTenant().getId(),
                AdminAuditActionType.USER_DISABLED,
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        null
                )
        );
    }

    @Transactional
    public void activateUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();

        if (target.getStatus() == UserStatus.ACTIVE) {
            return; // idempotent
        }
        target.activate();

        adminAuditEventService.record(
                actor.getId(),
                actor.getTenant().getId(),
                AdminAuditActionType.USER_ACTIVATED,
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        null
                )
        );
    }
}
