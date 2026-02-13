package com.brutecx.docflow_backend.domain.admin;

import com.brutecx.docflow_backend.api.dto.admin.AdminUserResponseDTO;
import com.brutecx.docflow_backend.api.error.SelfActionForbiddenException;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeMetadata;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeReason;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.security.AuthRoleExtractor;
import com.brutecx.docflow_backend.security.session.SessionRevocationService;
import com.brutecx.docflow_backend.domain.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final TenantService tenantService;

    /**
     * Platform admin user listing.
     * Tenant filter is now membership-based (UserSpecifications handles it).
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponseDTO> listUsers(
            Pageable pageable,
            UUID tenantId,
            UserStatus status,
            String emailSearch
    ) {

        User actor = userService.getRequiredCurrentUser();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        Specification<User> spec = Specification.allOf(
                UserSpecifications.tenant(tenantId),
                UserSpecifications.status(status),
                UserSpecifications.emailContains(emailSearch)
        );

        Page<User> page = userRepository.findAll(spec, pageable);

        List<String> subjectIds = page.stream()
                .map(User::getExternalSubjectId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, List<String>> rolesBySubject =
                keycloakAdminClient.fetchRealmRolesForUsers(subjectIds);

        // ---- SensitiveAccess Audit ----
        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.USER,
                rootTenantId.toString(),
                "USER_LIST",
                "READ",
                "/api/admin/users",
                null,
                null,
                null,
                "ADMIN_USER_LIST",
                "status=" + status + ",emailSearch=" + emailSearch,
                SensitiveDataClassification.CONFIDENTIAL,
                null
        );

        return page.map(user -> {

            List<String> roles =
                    authRoleExtractor.filterRealmRoles(
                            rolesBySubject.getOrDefault(
                                    user.getExternalSubjectId(),
                                    List.of()
                            )
                    );

            return new AdminUserResponseDTO(
                    user.getId(),
                    user.getEmail(),
                    user.getStatus(),
                    roles
            );
        });
    }

    @Transactional
    public void lockUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();

        if (actor.getId().equals(target.getId())) {
            throw new SelfActionForbiddenException("You cannot lock your own account");
        }

        if (target.getStatus() == UserStatus.LOCKED) {
            return;
        }

        target.lock();

        int revokedSessions =
                sessionRevocationService.revokeSessionsBySubject(
                        target.getExternalSubjectId(),
                        actor.getExternalSubjectId()
                );

        try {
            adminAuditEventService.record(
                    AdminAuditActionType.USER_LOCKED,
                    tenantService.getRootTenant().getId(),
                    actor.getExternalSubjectId(),
                    target.getId(),
                    new UserStateChangeMetadata(
                            UserStateChangeReason.MANUAL_ADMIN_ACTION,
                            "revokedSessions=" + revokedSessions
                    )
            );
        } catch (Exception e) {
            log.error(
                    "AUDIT FAILURE for USER_LOCKED actorId={} targetUserId={}",
                    actor.getId(),
                    target.getId(),
                    e
            );
            throw e;
        }
    }

    @Transactional
    public void disableUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();

        if (actor.getId().equals(target.getId())) {
            throw new SelfActionForbiddenException("You cannot disable your own account");
        }

        if (target.getStatus() == UserStatus.DISABLED) {
            return;
        }

        target.disable();

        int revokedSessions =
                sessionRevocationService.revokeSessionsBySubject(
                        target.getExternalSubjectId(),
                        actor.getExternalSubjectId()
                );

        adminAuditEventService.record(
                AdminAuditActionType.USER_DISABLED,
                tenantService.getRootTenant().getId(),
                actor.getExternalSubjectId(),
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        "revokedSessions=" + revokedSessions
                )
        );
    }

    @Transactional
    public void activateUser(UUID userId) {
        User target = userRepository.getRequired(userId);
        User actor = userService.getRequiredCurrentUser();

        if (target.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        target.activate();

        adminAuditEventService.record(
                AdminAuditActionType.USER_ACTIVATED,
                tenantService.getRootTenant().getId(),
                actor.getExternalSubjectId(),
                target.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        null
                )
        );
    }
}
