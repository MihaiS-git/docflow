package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseDTO;
import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseMapper;
import com.brutecx.docflow_backend.api.error.LastManagerViolationException;
import com.brutecx.docflow_backend.api.error.SelfActionForbiddenException;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.TenantAuditMetadata;
import com.brutecx.docflow_backend.audit.admin.TenantMembershipChangeMetadata;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String> ALLOWED_USER_SORT_FIELDS = List.of(
            "createdAt",
            "email",
            "firstName",
            "lastName",
            "role",
            "status"
    );

    private final TenantRepository tenantRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final IAdminAuditEventService adminAuditEventService;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;

    /* =====================================================
       USER LISTING (FILTERED + SAFE SORT)
       ===================================================== */

    public Page<TenantUserResponseDTO> listUsersByTenant(
            UUID tenantId,
            TenantRole role,
            MembershipStatus status,
            int page,
            int size,
            String sort,
            Sort.Direction direction
    ) {
        Objects.requireNonNull(tenantId, "tenantId");

        if (sort == null || sort.isBlank()) {
            sort = "createdAt";
        }
        if (direction == null) {
            direction = Sort.Direction.DESC;
        }
        if (!ALLOWED_USER_SORT_FIELDS.contains(sort)) {
            throw new IllegalArgumentException("Invalid sort field: " + sort);
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // Deterministic ordering: add id as tiebreaker
        Sort sortSpec = Sort.by(direction, mapSortField(sort))
                .and(Sort.by(Sort.Direction.ASC, "id"));

        Pageable pageable = PageRequest.of(safePage, safeSize, sortSpec);

        Page<UserTenantMembership> memberships =
                membershipRepository.findFilteredWithUser(
                        tenantId,
                        role,
                        status,
                        pageable
                );

        User actor = userService.getRequiredCurrentUser();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantId,
                SensitiveAccessSubjectType.USER,
                tenantId.toString(),
                "TENANT_USERS",
                "LIST",
                "/api/tenants/" + tenantId + "/users",
                null,
                null,
                null,
                "TENANT_LIST_USERS",
                null,
                SensitiveDataClassification.CONFIDENTIAL,
                null
        );

        return memberships.map(TenantUserResponseMapper::from);
    }

    private static String mapSortField(String field) {
        return switch (field) {
            // membership fields
            case "role" -> "role";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            // nested user fields (supported by Spring Data sort with property traversal)
            case "email" -> "user.email";
            case "firstName" -> "user.firstName";
            case "lastName" -> "user.lastName";
            default -> throw new IllegalArgumentException("Unsupported sort field: " + field);
        };
    }

    /* =====================================================
       EVERYTHING BELOW REMAINS UNCHANGED
       ===================================================== */

    @Transactional(readOnly = true)
    public TenantStatus getRequiredTenantStatus(UUID tenantId) {
        return tenantRepository.findStatusById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
    }

    @Transactional
    public Tenant getOrCreateBootstrapTenant() {
        return tenantRepository.findFirstByTenantType(TenantType.ROOT)
                .orElseGet(() -> tenantRepository.save(Tenant.bootstrapTenant("Company")));
    }

    @Transactional(readOnly = true)
    public Tenant getRootTenant() {
        return tenantRepository.findFirstByTenantType(TenantType.ROOT)
                .orElseThrow(() -> new IllegalStateException("ROOT tenant missing"));
    }

    @Transactional(readOnly = true)
    public Tenant getRequired(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
    }

    @Transactional(readOnly = true)
    public Page<Tenant> listAll(Pageable pageable) {
        return tenantRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tenant> listActive(Pageable pageable) {
        return tenantRepository.findByStatus(TenantStatus.ACTIVE, pageable);
    }

    /* =====================================================
       TENANT CREATION
       ===================================================== */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Tenant create(String name, String comment) {

        if (name == null || name.trim().isBlank()) {
            recordCreateFailureAudit(name);
            throw new IllegalArgumentException("Tenant name is required");
        }

        boolean success = false;
        RuntimeException failure = null;
        Tenant created = null;

        try {
            created = tenantRepository.save(new Tenant(name));

            // Ensure creator becomes MANAGER of the new tenant
            User actor = userService.getRequiredCurrentUser();
            if (!membershipRepository.existsByUserIdAndTenantId(
                    actor.getId(),
                    created.getId()
            )) {
                UserTenantMembership membership =
                        UserTenantMembership.create(
                                actor,
                                created,
                                TenantRole.MANAGER
                        );
                membershipRepository.save(membership);
            }

            success = true;
            return created;
        } catch (RuntimeException ex) {
            failure = ex;
            throw ex;

        } finally {

            UUID auditTenantId = success ? created.getId() : null;

            AdminAuditActionType actionType =
                    success
                            ? AdminAuditActionType.TENANT_CREATED
                            : AdminAuditActionType.TENANT_CREATE_FAILED;

            String subjectId =
                    success
                            ? created.getId().toString()
                            : "TENANT_CREATE:" + name.trim();

            String failureType =
                    success
                            ? null
                            : (failure != null ? failure.getClass().getSimpleName() : "UNKNOWN");

            TenantAuditMetadata metadata = new TenantAuditMetadata(
                    success ? created.getId().toString() : null,
                    "CREATE_TENANT",
                    success ? comment : failureType
            );

            adminAuditEventService.record(
                    actionType,
                    auditTenantId,
                    subjectId,
                    null,
                    metadata
            );
        }
    }

    /* =====================================================
       Mutations
       ===================================================== */

    @Transactional
    public void suspendTenant(UUID tenantId, String comment) {

        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        try {
            tenant.suspend();
            tenantRepository.save(tenant);

            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_SUSPENDED,
                    "SUSPEND_TENANT",
                    comment
            );

        } catch (TenantLifecycleViolationException ex) {

            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "SUSPEND_TENANT",
                    ex.getMessage()
            );

            throw ex;
        }
    }

    @Transactional
    public void reactivateTenant(UUID tenantId, String comment) {

        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        try {
            tenant.reactivate();
            tenantRepository.save(tenant);

            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_UPDATED,
                    "REACTIVATE_TENANT",
                    comment
            );

        } catch (TenantLifecycleViolationException ex) {

            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "REACTIVATE_TENANT",
                    ex.getMessage()
            );

            throw ex;
        }
    }

    @Transactional
    public void updateTenant(
            UUID tenantId,
            String newName,
            String newDataRegion,
            Long newRetentionDays,
            Boolean disableBootstrap,
            String comment
    ) {

        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        try {
            boolean changed = false;

            if (newName != null && !newName.isBlank() && !newName.equals(tenant.getName())) {
                tenant.updateName(newName);
                changed = true;
            }

            if (newDataRegion != null && !Objects.equals(newDataRegion, tenant.getDataRegion())) {
                tenant.updateDataRegion(newDataRegion);
                changed = true;
            }

            if (newRetentionDays != null && !Objects.equals(newRetentionDays, tenant.getRetentionDays())) {
                tenant.updateRetentionDays(newRetentionDays);
                changed = true;
            }

            if (Boolean.TRUE.equals(disableBootstrap) && tenant.isBootstrapEnabled()) {
                tenant.disableBootstrap();
                changed = true;
            }

            if (changed) {
                tenantRepository.save(tenant);
                recordTenantAdminAudit(
                        // audits remain tenant-specific here
                        tenant,
                        AdminAuditActionType.TENANT_UPDATED,
                        "UPDATE_TENANT",
                        comment
                );
            }

        } catch (TenantLifecycleViolationException ex) {
            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "UPDATE_TENANT",
                    ex.getMessage()
            );

            throw ex;
        }
    }

     /* =====================================================
        Membership management
        ===================================================== */

    @Transactional
    public void updateMembership(
            UUID tenantId,
            UUID targetUserId,
            TenantRole newRole,
            MembershipStatus newStatus,
            String comment
    ) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetUserId, "targetUserId");

        User actor = userService.getRequiredCurrentUser();
        boolean actorIsTarget = actor.getId().equals(targetUserId);

        // Self-protection: prevent a MANAGER from demoting or suspending themselves
        if (actorIsTarget) {
            if (newRole != null && newRole != TenantRole.MANAGER) {
                throw new SelfActionForbiddenException("Self-demotion is not allowed");
            }
            if (newStatus == MembershipStatus.SUSPENDED) {
                throw new SelfActionForbiddenException("Self-suspension is not allowed");
            }
        }

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(targetUserId, tenantId)
                        .orElseThrow(() -> new IllegalStateException("Membership not found"));

        TenantRole oldRole = membership.getRole();
        MembershipStatus oldStatus = membership.getStatus();

        Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

        // ---------------- ROLE CHANGE ----------------
        if (newRole != null && newRole != oldRole) {

            if (oldRole == TenantRole.MANAGER && newRole != TenantRole.MANAGER) {
                ensureNotLastActiveManager(tenantId);
            }

            membership.changeRole(newRole);
            membershipRepository.save(membership);

            AdminAuditActionType roleAction =
                    (newRole.ordinal() > oldRole.ordinal())
                            ? AdminAuditActionType.ROLE_ASSIGNED
                            : AdminAuditActionType.ROLE_REVOKED;

            TenantMembershipChangeMetadata metadata =
                    new TenantMembershipChangeMetadata(
                            tenantId.toString(),
                            targetUserId.toString(),
                            "UPDATE_TENANT_MEMBERSHIP_ROLE",
                            oldRole,
                            newRole,
                            oldStatus,
                            membership.getStatus(),
                            comment
                    );

            adminAuditEventService.record(
                    roleAction,
                    tenantId,
                    actor.getExternalSubjectId(),
                    targetUserId,
                    metadata
            );

            securityLog.info(
                    "TENANT_MEMBERSHIP_ROLE_CHANGED tenantId={} actorUserId={} actorSubjectId={} targetUserId={} oldRole={} newRole={} oldStatus={} newStatus={}",
                    tenantId,
                    actor.getId(),
                    actor.getExternalSubjectId(),
                    targetUserId,
                    oldRole,
                    newRole,
                    oldStatus,
                    membership.getStatus()
            );

            oldRole = newRole; // refresh for subsequent status change
        }

        // ---------------- STATUS CHANGE ----------------
        if (newStatus != null && newStatus != oldStatus) {

            if (oldRole == TenantRole.MANAGER && newStatus == MembershipStatus.SUSPENDED) {
                ensureNotLastActiveManager(tenantId);
            }

            if (newStatus == MembershipStatus.SUSPENDED) {
                membership.suspend();
            } else {
                membership.activate();
            }

            membershipRepository.save(membership);

            TenantMembershipChangeMetadata metadata =
                    new TenantMembershipChangeMetadata(
                            tenantId.toString(),
                            targetUserId.toString(),
                            newStatus == MembershipStatus.SUSPENDED
                                    ? "SUSPEND_TENANT_MEMBERSHIP"
                                    : "ACTIVATE_TENANT_MEMBERSHIP",
                            oldRole,
                            membership.getRole(),
                            oldStatus,
                            newStatus,
                            comment
                    );

            adminAuditEventService.record(
                    newStatus == MembershipStatus.SUSPENDED
                            ? AdminAuditActionType.USER_LOCKED
                            : AdminAuditActionType.USER_ACTIVATED,
                    tenantId,
                    actor.getExternalSubjectId(),
                    targetUserId,
                    metadata
            );

            securityLog.info(
                    "TENANT_MEMBERSHIP_STATUS_CHANGED tenantId={} actorUserId={} actorSubjectId={} targetUserId={} oldRole={} newRole={} oldStatus={} newStatus={}",
                    tenantId,
                    actor.getId(),
                    actor.getExternalSubjectId(),
                    targetUserId,
                    oldRole,
                    membership.getRole(),
                    oldStatus,
                    newStatus
            );
        }
    }

    private void recordTenantAdminAudit(
            Tenant tenant,
            AdminAuditActionType actionType,
            String operation,
            String comment
    ) {

        TenantAuditMetadata metadata = new TenantAuditMetadata(
                tenant.getId().toString(),
                operation,
                comment
        );

        adminAuditEventService.record(
                actionType,
                tenant.getId(),
                tenant.getId().toString(),
                null,
                metadata
        );
    }

    private void recordCreateFailureAudit(String name) {

        TenantAuditMetadata metadata = new TenantAuditMetadata(
                null,
                "CREATE_TENANT",
                "VALIDATION_FAILED"
        );

        adminAuditEventService.record(
                AdminAuditActionType.TENANT_CREATE_FAILED,
                null,
                "TENANT_CREATE:" + (name == null ? "NULL" : name.trim()),
                null,
                metadata
        );
    }

    private void ensureNotLastActiveManager(UUID tenantId) {
        List<UUID> locked =
                membershipRepository.lockActiveManagers(
                        tenantId,
                        TenantRole.MANAGER,
                        MembershipStatus.ACTIVE
                );

        if (locked.size() <= 1) {
            throw new LastManagerViolationException("Cannot remove or suspend last MANAGER in tenant");
        }
    }
}
