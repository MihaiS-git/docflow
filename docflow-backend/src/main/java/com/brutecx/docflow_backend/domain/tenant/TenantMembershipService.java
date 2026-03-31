package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.error.*;
import com.brutecx.docflow_backend.audit.admin.*;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class TenantMembershipService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final IAdminAuditEventService adminAuditEventService;
    private final UserService userService;

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Transactional
    public void ensureMembership(UUID userId, UUID tenantId, TenantRole role) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");

        User user = userRepository.getReferenceById(userId);
        Tenant tenant = tenantRepository.getReferenceById(tenantId);

        membershipRepository.findByUserIdAndTenantId(userId, tenantId)
                .ifPresentOrElse(existing -> {

                    if (existing.getRole() == null || role.isMorePrivilegedThan(existing.getRole())) {
                        existing.changeRole(role);
                    }

                    if (existing.getStatus() != MembershipStatus.ACTIVE) {
                        existing.activate();
                    }

                }, () -> membershipRepository.save(
                        UserTenantMembership.create(user, tenant, role)
                ));
    }

    @Transactional
    public void updateMembership(
            UUID tenantId,
            UUID userId,
            TenantRole newRole,
            MembershipStatus newStatus,
            String comment
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        if (comment == null || comment.isBlank()) {
            throw new TenantInvalidArgumentException("comment is required");
        }

        User actor = userService.getRequiredCurrentUser();
        UUID actorUserId = actor.getId();

        if (actorUserId.equals(userId)) {
            throw new SelfActionForbiddenException(
                    "You cannot modify your own tenant membership"
            );
        }

        String subjectId = null;

        try {
            Tenant tenant = tenantRepository.getReferenceById(tenantId);

            UserTenantMembership actorMembership = membershipRepository
                    .findByUserIdAndTenantId(actorUserId, tenantId)
                    .orElseThrow(() -> new LifecycleAccessDeniedException(
                            ErrorCode.LIFECYCLE_ACCESS_DENIED,
                            "Actor is not a member of this tenant"
                    ));

            if (actorMembership.getStatus() != MembershipStatus.ACTIVE) {
                throw new LifecycleAccessDeniedException(
                        ErrorCode.LIFECYCLE_ACCESS_DENIED,
                        "Actor membership is not active"
                );
            }

            if (actorMembership.getRole() != TenantRole.MANAGER) {
                throw new LifecycleAccessDeniedException(
                        ErrorCode.LIFECYCLE_ACCESS_DENIED,
                        "Only MANAGER can update tenant memberships"
                );
            }

            UserTenantMembership membership = membershipRepository
                    .findByUserIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new TenantNotFoundException("Membership not found"));

            subjectId = membership.getId().toString();

            TenantRole oldRole = membership.getRole();
            MembershipStatus oldStatus = membership.getStatus();

            boolean roleChanged = newRole != null && newRole != oldRole;
            boolean statusChanged = newStatus != null && newStatus != oldStatus;

            if (!roleChanged && !statusChanged) {
                return;
            }

            enforceGuards(tenant, userId, newRole, newStatus);

            log.info(
                    "tenant_membership_update_requested",
                    kv("event.category", "admin"),
                    kv("event.action", "tenant_membership_update_requested"),
                    kv("tenant.id", tenantId),
                    kv("actor.user.id", actorUserId),
                    kv("target.user.id", userId),
                    kv("subject.id", subjectId),
                    kv("old.role", oldRole),
                    kv("new.role", newRole),
                    kv("old.status", oldStatus),
                    kv("new.status", newStatus),
                    kv("comment.present", true)
            );

            if ((roleChanged && oldRole == TenantRole.MANAGER && newRole != TenantRole.MANAGER)
                    || (statusChanged && oldRole == TenantRole.MANAGER && newStatus == MembershipStatus.SUSPENDED)) {
                ensureNotLastManager(tenantId);
            }

            if (roleChanged) {
                membership.changeRole(newRole);
            }

            if (statusChanged) {
                if (newStatus == MembershipStatus.ACTIVE) {
                    membership.activate();
                } else {
                    membership.suspend();
                }
            }

            TenantMembershipChangeMetadata metadata =
                    new TenantMembershipChangeMetadata(
                            tenantId.toString(),
                            userId.toString(),
                            resolveOperation(roleChanged, statusChanged),
                            oldRole,
                            newRole,
                            oldStatus,
                            newStatus,
                            comment
                    );

            adminAuditEventService.record(
                    AdminAuditActionType.TENANT_UPDATED,
                    tenantId,
                    subjectId,
                    userId,
                    metadata
            );

            if (roleChanged) {
                adminAuditEventService.record(
                        newRole.isMorePrivilegedThan(oldRole)
                                ? AdminAuditActionType.ROLE_ASSIGNED
                                : AdminAuditActionType.ROLE_REVOKED,
                        tenantId,
                        subjectId,
                        userId,
                        new RoleChangeMetadata(newRole.name(), comment)
                );
            }

            if (statusChanged) {
                adminAuditEventService.record(
                        newStatus == MembershipStatus.ACTIVE
                                ? AdminAuditActionType.USER_ACTIVATED
                                : AdminAuditActionType.USER_DISABLED,
                        tenantId,
                        subjectId,
                        userId,
                        new UserStateChangeMetadata(
                                UserStateChangeReason.MANUAL_ADMIN_ACTION,
                                comment
                        )
                );
            }

            log.info(
                    "tenant_membership_updated",
                    kv("event.category", "admin"),
                    kv("event.action", "tenant_membership_updated"),
                    kv("tenant.id", tenantId),
                    kv("actor.user.id", actorUserId),
                    kv("target.user.id", userId),
                    kv("subject.id", subjectId),
                    kv("operation", resolveOperation(roleChanged, statusChanged)),
                    kv("old.role", oldRole),
                    kv("new.role", newRole),
                    kv("old.status", oldStatus),
                    kv("new.status", newStatus)
            );

        } catch (Exception ex) {
            log.warn(
                    "tenant_membership_update_failed",
                    kv("event.category", "admin"),
                    kv("event.action", "tenant_membership_update_failed"),
                    kv("tenant.id", tenantId),
                    kv("actor.user.id", actorUserId),
                    kv("target.user.id", userId),
                    kv("subject.id", subjectId),
                    kv("error.type", ex.getClass().getSimpleName()),
                    kv("error.message", ex.getMessage())
            );
            throw ex;
        }
    }

    private void enforceGuards(
            Tenant tenant,
            UUID targetUserId,
            TenantRole newRole,
            MembershipStatus newStatus
    ) {
        boolean isOwner = tenant.getOwner() != null
                && tenant.getOwner().getId().equals(targetUserId);

        if (isOwner) {
            if (newRole != null && newRole != TenantRole.MANAGER) {
                throw new TenantException(
                        ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                        "Owner role cannot be changed"
                );
            }

            if (newStatus != null && newStatus != MembershipStatus.ACTIVE) {
                throw new TenantException(
                        ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                        "Owner must remain ACTIVE"
                );
            }
        }
    }

    private void ensureNotLastManager(UUID tenantId) {
        long managers = membershipRepository.countByTenantIdAndRoleAndStatus(
                tenantId,
                TenantRole.MANAGER,
                MembershipStatus.ACTIVE
        );

        if (managers <= 1) {
            throw new LastManagerViolationException(
                    "Cannot modify last active manager"
            );
        }
    }

    private String resolveOperation(boolean roleChanged, boolean statusChanged) {
        if (roleChanged && statusChanged) return "ROLE_AND_STATUS_UPDATED";
        if (roleChanged) return "ROLE_UPDATED";
        return "STATUS_UPDATED";
    }
}