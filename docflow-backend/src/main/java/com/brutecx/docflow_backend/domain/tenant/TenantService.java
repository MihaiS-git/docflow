package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantFilter;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO;
import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseDTO;
import com.brutecx.docflow_backend.api.error.*;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.TenantAuditMetadata;
import com.brutecx.docflow_backend.audit.admin.TenantMembershipChangeMetadata;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventType;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.entries;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String> ALLOWED_USER_SORT_FIELDS = List.of(
            "createdAt",
            "email",
            "firstName",
            "lastName",
            "role",
            "status"
    );

    private static final String TENANT_NAME_UNIQUE_CONSTRAINT = "ux_tenants_name_ci";

    private final TenantRepository tenantRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final TenantMembershipService tenantMembershipService;
    private final IAdminAuditEventService adminAuditEventService;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final AuditRequestContextExtractor contextExtractor;

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

        TenantStatus tenantStatus = getRequiredTenantStatus(tenantId);
        if (tenantStatus != TenantStatus.ACTIVE) {
            throw new LifecycleAccessDeniedException(
                    ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                    "Tenant is not active: " + tenantId
            );
        }

        if (sort == null || sort.isBlank()) {
            sort = "createdAt";
        }
        if (direction == null) {
            direction = Sort.Direction.DESC;
        }
        if (!ALLOWED_USER_SORT_FIELDS.contains(sort)) {
            throw new TenantInvalidArgumentException("Invalid sort field: " + sort);
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Sort sortSpec = Sort.by(direction, mapSortField(sort))
                .and(Sort.by(Sort.Direction.ASC, "id"));

        Pageable pageable = PageRequest.of(safePage, safeSize, sortSpec);

        Page<UserTenantMembership> memberships;

        if (role != null && status != null) {
            memberships = membershipRepository.findByTenantIdAndRoleAndStatusWithUser(
                    tenantId,
                    role,
                    status,
                    pageable
            );
        } else if (role != null) {
            memberships = membershipRepository.findByTenantIdAndRoleWithUser(
                    tenantId,
                    role,
                    pageable
            );
        } else if (status != null) {
            memberships = membershipRepository.findByTenantIdAndStatusWithUser(
                    tenantId,
                    status,
                    pageable
            );
        } else {
            memberships = membershipRepository.findByTenantIdWithUser(
                    tenantId,
                    pageable
            );
        }

        User actor = userService.getRequiredCurrentUser();
        String resourcePath = contextExtractor.fromCurrentRequest().resourcePath();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantId,
                SensitiveAccessSubjectType.USER,
                tenantId.toString(),
                "TENANT_USERS",
                "LIST",
                resourcePath,
                "TENANT_LIST_USERS",
                null,
                SensitiveDataClassification.CONFIDENTIAL
        );

        return memberships.map(TenantUserResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public TenantStatus getRequiredTenantStatus(UUID tenantId) {
        return tenantRepository.findStatusById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional
    public Tenant getOrCreateBootstrapTenant() {
        return tenantRepository.findFirstByTenantType(TenantType.ROOT)
                .orElseGet(() -> {
                    final String bootstrapName = Tenant.normalizeName("Company");

                    try {
                        Tenant existingByName = tenantRepository.findFirstByTenantType(TenantType.ROOT)
                                .orElse(null);
                        if (existingByName != null) {
                            return existingByName;
                        }

                        if (tenantRepository.existsByNameIgnoreCase(bootstrapName)) {
                            throw new TenantAlreadyExistsException("Tenant name already exists");
                        }

                        return tenantRepository.save(Tenant.bootstrapTenant(bootstrapName));
                    } catch (RuntimeException ex) {
                        InfraEventLogger.failure(
                                InfraEventType.DATABASE,
                                InfraEventActions.DB_ENTITY_PERSIST,
                                "bootstrap_tenant_persist_failure",
                                ex
                        );
                        throw ex;
                    }
                });
    }

    @Transactional(readOnly = true)
    public Tenant getRootTenant() {
        return tenantRepository.findFirstByTenantType(TenantType.ROOT)
                .orElseThrow(() -> new TenantNotFoundException("ROOT tenant missing"));
    }

    @Transactional(readOnly = true)
    public Tenant getRequired(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional(readOnly = true)
    public Tenant getRequiredWithOwner(UUID tenantId) {
        return tenantRepository.findByIdWithOwner(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional(readOnly = true)
    public Page<TenantListItemDTO> listAll(TenantFilter filter, Pageable pageable) {
        Page<Tenant> page =
                tenantRepository.findAll(
                        TenantSpecification.fromFilter(filter),
                        pageable
                );

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> ids =
                page.getContent()
                        .stream()
                        .map(Tenant::getId)
                        .toList();

        List<TenantListItemDTO> rows =
                tenantRepository.fetchAdminRows(ids);

        Map<UUID, Integer> order = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }

        rows.sort(Comparator.comparingInt(a -> order.get(a.id())));

        Map<UUID, TenantListItemDTO> rowMap =
                rows.stream()
                        .collect(Collectors.toMap(
                                TenantListItemDTO::id,
                                r -> r,
                                (a, b) -> a,
                                () -> new HashMap<>(ids.size())
                        ));

        List<TenantListItemDTO> ordered =
                page.getContent()
                        .stream()
                        .map(t -> rowMap.get(t.getId()))
                        .filter(Objects::nonNull)
                        .toList();

        return new PageImpl<>(
                ordered,
                pageable,
                page.getTotalElements()
        );
    }

    @Transactional
    public void assignOwner(UUID tenantId, UUID userId) {
        Tenant tenant = getRequired(tenantId);

        User user = userService.getRequired(userId);
        assignOwnerInvariant(tenant, user);
        tenantRepository.save(tenant);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Tenant create(String name, String description, String dataRegion, Integer retentionDays) {
        boolean success = false;
        RuntimeException failure = null;
        Tenant created = null;

        try {
            final String normalizedName = Tenant.normalizeName(name);
            requireUniqueTenantNameForCreate(normalizedName);

            try {
                Tenant tenant = buildTenant(normalizedName, description, dataRegion, retentionDays);
                created = tenantRepository.save(tenant);
            } catch (DataIntegrityViolationException ex) {
                if (isTenantNameUniqueViolation(ex)) {
                    throw new TenantAlreadyExistsException("Tenant name already exists");
                }
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_create_persist_failure",
                        ex
                );
                throw ex;
            } catch (RuntimeException ex) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_create_persist_failure",
                        ex
                );
                throw ex;
            }

            User actor = userService.getRequiredCurrentUser();

            try {
                tenantMembershipService.ensureMembership(
                        actor.getId(),
                        created.getId(),
                        TenantRole.MANAGER
                );

                assignOwnerInvariant(created, actor);
                tenantRepository.save(created);
            } catch (RuntimeException ex) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_create_membership_persist_failure",
                        ex
                );
                throw ex;
            }

            success = true;
            return created;
        } catch (RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            safeAuditCreate(success, created, name, description, failure);
        }
    }

    @Transactional
    public void suspendTenant(UUID tenantId, String comment) {
        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        User actor = userService.getRequiredCurrentUser();
        requireOwner(tenant, actor);

        try {
            tenant.suspend();
            try {
                tenantRepository.save(tenant);
            } catch (RuntimeException ex) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_suspend_persist_failure",
                        ex
                );
                throw ex;
            }

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
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        User actor = userService.getRequiredCurrentUser();
        requireOwner(tenant, actor);

        try {
            tenant.reactivate();
            try {
                tenantRepository.save(tenant);
            } catch (RuntimeException ex) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_reactivate_persist_failure",
                        ex
                );
                throw ex;
            }

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
    public void terminateTenant(UUID tenantId, String comment) {
        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        User actor = userService.getRequiredCurrentUser();
        requireOwner(tenant, actor);

        try {
            tenant.terminate();
            try {
                tenantRepository.save(tenant);
            } catch (RuntimeException ex) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_terminate_persist_failure",
                        ex
                );
                throw ex;
            }

            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_UPDATED,
                    "TERMINATE_TENANT",
                    comment
            );
        } catch (TenantLifecycleViolationException ex) {
            recordTenantAdminAudit(
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "TERMINATE_TENANT",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Transactional
    public void updateTenant(
            UUID tenantId,
            String newName,
            String description,
            String newDataRegion,
            Integer newRetentionDays,
            Boolean disableBootstrap,
            String comment
    ) {
        Objects.requireNonNull(tenantId, "tenantId");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        User actor = userService.getRequiredCurrentUser();
        requireOwner(tenant, actor);

        try {
            boolean changed = false;

            if (newName != null && !newName.isBlank()) {
                String normalizedNewName = Tenant.normalizeName(newName);

                if (!normalizedNewName.equals(tenant.getName())) {
                    requireUniqueTenantNameForUpdate(tenantId, normalizedNewName);
                    tenant.updateName(normalizedNewName);
                    changed = true;
                }
            }

            if (description != null && !Objects.equals(description, tenant.getDescription())) {
                tenant.updateDescription(description);
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
                try {
                    tenantRepository.save(tenant);
                } catch (DataIntegrityViolationException ex) {
                    if (isTenantNameUniqueViolation(ex)) {
                        throw new TenantAlreadyExistsException("Tenant name already exists");
                    }
                    InfraEventLogger.failure(
                            InfraEventType.DATABASE,
                            InfraEventActions.DB_ENTITY_PERSIST,
                            "tenant_update_persist_failure",
                            ex
                    );
                    throw ex;
                } catch (RuntimeException ex) {
                    InfraEventLogger.failure(
                            InfraEventType.DATABASE,
                            InfraEventActions.DB_ENTITY_PERSIST,
                            "tenant_update_persist_failure",
                            ex
                    );
                    throw ex;
                }

                recordTenantAdminAudit(
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
        Tenant tenant = getRequired(tenantId);

        enforceMembershipGuards(
                tenant,
                actor,
                targetUserId,
                newRole,
                newStatus
        );

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(targetUserId, tenantId)
                        .orElseThrow(() -> new TenantNotFoundException("Membership not found"));

        TenantRole oldRole = membership.getRole();
        MembershipStatus oldStatus = membership.getStatus();

        oldRole = applyRoleChange(
                tenantId,
                targetUserId,
                actor,
                membership,
                oldRole,
                oldStatus,
                newRole,
                comment
        );

        applyStatusChange(
                tenantId,
                targetUserId,
                actor,
                membership,
                oldRole,
                oldStatus,
                newStatus,
                comment
        );
    }

    @Transactional(readOnly = true)
    public List<TenantLookupDTO> listManagedTenantsForCurrentUser() {
        User actor = userService.getRequiredCurrentUser();

        return membershipRepository.findActiveManagedTenantLookup(
                actor.getId(),
                TenantRole.MANAGER,
                MembershipStatus.ACTIVE,
                TenantStatus.ACTIVE
        );
    }

    private static String mapSortField(String field) {
        return switch (field) {
            case "role" -> "role";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "email" -> "user.email";
            case "firstName" -> "user.firstName";
            case "lastName" -> "user.lastName";
            default -> throw new TenantInvalidArgumentException("Unsupported sort field: " + field);
        };
    }

    private static String extractConstraintName(Throwable ex) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }

        return null;
    }

    private static String resolveCreateFailureType(RuntimeException failure) {
        if (failure == null) {
            return "UNKNOWN";
        }

        if (failure instanceof TenantException ex) {
            return ex.getMessage();
        }

        return failure.getClass().getSimpleName();
    }

    private static boolean isTenantNameUniqueViolation(DataIntegrityViolationException ex) {
        String constraint = extractConstraintName(ex);
        return TENANT_NAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraint);
    }

    private void requireUniqueTenantNameForCreate(String normalizedName) {
        if (tenantRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new TenantAlreadyExistsException("Tenant name already exists");
        }
    }

    private void requireUniqueTenantNameForUpdate(UUID tenantId, String normalizedName) {
        if (tenantRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, tenantId)) {
            throw new TenantAlreadyExistsException("Tenant name already exists");
        }
    }

    private void securityWarnDenied(
            String eventAction,
            UUID tenantId,
            User actor,
            UUID targetUserId,
            String errorCode
    ) {
        String correlationId = contextExtractor.fromCurrentRequest().correlationId();

        Map<String, Object> fields = new HashMap<>();
        fields.put("schema_version", "docflow_siem_v1");
        fields.put("correlation.id", correlationId);
        fields.put("event.category", "security");
        fields.put("event.type", "tenant_membership");
        fields.put("event.action", eventAction);
        fields.put("event.outcome", "failure");
        fields.put("error.code", errorCode);
        fields.put("tenant.id", tenantId);
        fields.put("actor.user_id", actor != null ? actor.getId() : null);
        fields.put("actor.subject_id", actor != null ? actor.getExternalSubjectId() : null);
        fields.put("target.user_id", targetUserId);

        SECURITY_LOG.warn("security_event {}", entries(fields));
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

    private void ensureNotLastActiveManager(UUID tenantId) {
        final List<UUID> locked;

        try {
            locked =
                    membershipRepository.lockActiveManagers(
                            tenantId,
                            TenantRole.MANAGER,
                            MembershipStatus.ACTIVE
                    );
        } catch (RuntimeException ex) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_QUERY_EXECUTE,
                    "lock_active_managers_failure",
                    ex
            );
            throw ex;
        }

        if (locked.size() <= 1) {
            throw new LastManagerViolationException("Cannot remove or suspend last MANAGER in tenant");
        }
    }

    private void assignOwnerInvariant(Tenant tenant, User user) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(user, "user");

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
                        .orElseThrow(() -> new TenantException(
                                ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                                "Owner must be a member of the tenant"
                        ));

        if (membership.getRole() != TenantRole.MANAGER) {
            throw new TenantException(
                    ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                    "Owner must have MANAGER role"
            );
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new TenantException(
                    ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                    "Owner must be ACTIVE"
            );
        }

        tenant.assignOwner(user);
    }

    private void enforceMembershipGuards(
            Tenant tenant,
            User actor,
            UUID targetUserId,
            TenantRole newRole,
            MembershipStatus newStatus
    ) {
        boolean actorIsTarget = actor.getId().equals(targetUserId);

        boolean isOwner = tenant.getOwner() != null
                && tenant.getOwner().getId().equals(targetUserId);

        if (isOwner) {
            if (newRole != null && newRole != TenantRole.MANAGER) {
                securityWarnDenied(
                        "TENANT_OWNER_ROLE_CHANGE_DENIED",
                        tenant.getId(),
                        actor,
                        targetUserId,
                        "OWNER_ROLE_CHANGE_DENIED"
                );
                throw new TenantException(
                        ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                        "Owner role cannot be changed"
                );
            }

            if (newStatus != null && newStatus != MembershipStatus.ACTIVE) {
                securityWarnDenied(
                        "TENANT_OWNER_STATUS_CHANGE_DENIED",
                        tenant.getId(),
                        actor,
                        targetUserId,
                        "OWNER_STATUS_CHANGE_DENIED"
                );
                throw new TenantException(
                        ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                        "Owner must remain ACTIVE"
                );
            }
        }

        if (actorIsTarget) {
            if (newRole != null && newRole != TenantRole.MANAGER) {
                securityWarnDenied(
                        "TENANT_MEMBERSHIP_SELF_DEMOTION_DENIED",
                        tenant.getId(),
                        actor,
                        targetUserId,
                        "SELF_DEMOTION_DENIED"
                );
                throw new SelfActionForbiddenException("Self-demotion is not allowed");
            }

            if (newStatus == MembershipStatus.SUSPENDED) {
                securityWarnDenied(
                        "TENANT_MEMBERSHIP_SELF_SUSPEND_DENIED",
                        tenant.getId(),
                        actor,
                        targetUserId,
                        "SELF_SUSPEND_DENIED"
                );
                throw new SelfActionForbiddenException("Self-suspension is not allowed");
            }
        }
    }

    private TenantRole applyRoleChange(
            UUID tenantId,
            UUID targetUserId,
            User actor,
            UserTenantMembership membership,
            TenantRole oldRole,
            MembershipStatus oldStatus,
            TenantRole newRole,
            String comment
    ) {
        Objects.requireNonNull(newRole, "newRole");
        if (newRole.equals(oldRole)) {
            return oldRole;
        }

        if (oldRole == TenantRole.MANAGER
                && oldStatus == MembershipStatus.ACTIVE) {
            try {
                ensureNotLastActiveManager(tenantId);
            } catch (LastManagerViolationException ex) {
                securityWarnDenied(
                        "TENANT_MEMBERSHIP_LAST_MANAGER_DENIED",
                        tenantId,
                        actor,
                        targetUserId,
                        "LAST_MANAGER_VIOLATION"
                );
                throw ex;
            }
        }

        membership.changeRole(newRole);

        try {
            membershipRepository.save(membership);
        } catch (RuntimeException ex) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_membership_role_persist_failure",
                    ex
            );
            throw ex;
        }

        AdminAuditActionType roleAction =
                newRole.isMorePrivilegedThan(oldRole)
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

        logRoleChangeSecurityEvent(
                tenantId,
                actor,
                targetUserId,
                oldRole,
                newRole,
                oldStatus,
                membership.getStatus()
        );

        return newRole;
    }

    private void applyStatusChange(
            UUID tenantId,
            UUID targetUserId,
            User actor,
            UserTenantMembership membership,
            TenantRole currentRole,
            MembershipStatus oldStatus,
            MembershipStatus newStatus,
            String comment
    ) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (newStatus.equals(oldStatus)) {
            return;
        }

        if (currentRole == TenantRole.MANAGER
                && oldStatus == MembershipStatus.ACTIVE
                && newStatus == MembershipStatus.SUSPENDED) {
            try {
                ensureNotLastActiveManager(tenantId);
            } catch (LastManagerViolationException ex) {
                securityWarnDenied(
                        "TENANT_MEMBERSHIP_LAST_MANAGER_DENIED",
                        tenantId,
                        actor,
                        targetUserId,
                        "LAST_MANAGER_VIOLATION"
                );
                throw ex;
            }
        }

        if (newStatus == MembershipStatus.SUSPENDED) {
            membership.suspend();
        } else {
            membership.activate();
        }

        try {
            membershipRepository.save(membership);
        } catch (RuntimeException ex) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_membership_status_persist_failure",
                    ex
            );
            throw ex;
        }

        TenantMembershipChangeMetadata metadata =
                new TenantMembershipChangeMetadata(
                        tenantId.toString(),
                        targetUserId.toString(),
                        newStatus == MembershipStatus.SUSPENDED
                                ? "SUSPEND_TENANT_MEMBERSHIP"
                                : "ACTIVATE_TENANT_MEMBERSHIP",
                        currentRole,
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

        logStatusChangeSecurityEvent(
                tenantId,
                actor,
                targetUserId,
                membership.getRole(),
                oldStatus,
                newStatus
        );
    }

    private void logRoleChangeSecurityEvent(
            UUID tenantId,
            User actor,
            UUID targetUserId,
            TenantRole oldRole,
            TenantRole newRole,
            MembershipStatus oldStatus,
            MembershipStatus newStatus
    ) {
        String correlationId = contextExtractor.fromCurrentRequest().correlationId();

        Map<String, Object> fields = new HashMap<>();
        fields.put("schema_version", "docflow_siem_v1");
        fields.put("correlation.id", correlationId);
        fields.put("event.category", "security");
        fields.put("event.type", "tenant_membership");
        fields.put("event.action", "TENANT_MEMBERSHIP_ROLE_CHANGED");
        fields.put("event.outcome", "success");
        fields.put("tenant.id", tenantId);
        fields.put("actor.user_id", actor.getId());
        fields.put("actor.subject_id", actor.getExternalSubjectId());
        fields.put("target.user_id", targetUserId);
        fields.put("membership.old_role", oldRole.name());
        fields.put("membership.new_role", newRole.name());
        fields.put("membership.old_status", oldStatus.name());
        fields.put("membership.new_status", newStatus.name());

        SECURITY_LOG.info("security_event {}", entries(fields));
    }

    private void logStatusChangeSecurityEvent(
            UUID tenantId,
            User actor,
            UUID targetUserId,
            TenantRole role,
            MembershipStatus oldStatus,
            MembershipStatus newStatus
    ) {
        String correlationId = contextExtractor.fromCurrentRequest().correlationId();

        Map<String, Object> fields = new HashMap<>();
        fields.put("schema_version", "docflow_siem_v1");
        fields.put("correlation.id", correlationId);
        fields.put("event.category", "security");
        fields.put("event.type", "tenant_membership");
        fields.put("event.action", "TENANT_MEMBERSHIP_STATUS_CHANGED");
        fields.put("event.outcome", "success");
        fields.put("tenant.id", tenantId);
        fields.put("actor.user_id", actor.getId());
        fields.put("actor.subject_id", actor.getExternalSubjectId());
        fields.put("target.user_id", targetUserId);
        fields.put("membership.role", role.name());
        fields.put("membership.old_status", oldStatus.name());
        fields.put("membership.new_status", newStatus.name());

        SECURITY_LOG.info("security_event {}", entries(fields));
    }

    private void safeAuditCreate(
            boolean success,
            Tenant created,
            String name,
            String description,
            RuntimeException failure
    ) {
        UUID auditTenantId = success ? created.getId() : null;

        AdminAuditActionType actionType =
                success
                        ? AdminAuditActionType.TENANT_CREATED
                        : AdminAuditActionType.TENANT_CREATE_FAILED;

        String subjectId =
                success
                        ? created.getId().toString()
                        : "TENANT_CREATE:" + (name == null ? "NULL" : name.trim());

        String failureType =
                success
                        ? null
                        : resolveCreateFailureType(failure);

        TenantAuditMetadata metadata = new TenantAuditMetadata(
                success ? created.getId().toString() : null,
                "CREATE_TENANT",
                success ? description : failureType
        );

        try {
            adminAuditEventService.record(
                    actionType,
                    auditTenantId,
                    subjectId,
                    null,
                    metadata
            );
        } catch (RuntimeException auditEx) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_create_audit_persist_failure",
                    auditEx
            );
        }
    }

    @Nonnull
    private static Tenant buildTenant(String normalizedName, String description, String dataRegion, Integer retentionDays) {
        Tenant tenant = new Tenant(normalizedName);

        if (description != null && !description.isBlank()) {
            tenant.updateDescription(description);
        }

        if (dataRegion != null && !dataRegion.isBlank()) {
            tenant.updateDataRegion(dataRegion);
        }

        if (retentionDays != null) {
            tenant.updateRetentionDays(retentionDays);
        }

        return tenant;
    }

    private void requireOwner(Tenant tenant, User actor) {
        if (!tenant.isOwner(actor)) {
            throw new TenantException(
                    ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                    "Only tenant owner can perform this operation"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireActiveTenant(UUID tenantId) {
        TenantStatus status = getRequiredTenantStatus(tenantId);

        if (status != TenantStatus.ACTIVE) {
            throw new LifecycleAccessDeniedException(
                    ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                    "Tenant is not active: " + tenantId
            );
        }
    }
}