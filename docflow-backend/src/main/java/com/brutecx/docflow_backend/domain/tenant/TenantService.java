package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantFilter;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO;
import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseDTO;
import com.brutecx.docflow_backend.api.error.*;
import com.brutecx.docflow_backend.audit.AuditPublisher;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.TenantAuditMetadata;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {


    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_USER_SORT_FIELDS = Set.of(
            "displayName",
            "jobTitle",
            "department",
            "role",
            "status",
            "createdAt",
            "updatedAt"
    );

    private static final String TENANT_NAME_UNIQUE_CONSTRAINT = "ux_tenants_name_ci";

    private final TenantRepository tenantRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final TenantMembershipService tenantMembershipService;
    private final IAdminAuditEventService adminAuditEventService;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    public Page<TenantUserResponseDTO> listUsersByTenant(
            UUID tenantId,
            TenantRole role,
            MembershipStatus status,
            String search,
            String jobTitle,
            String department,
            LocalDate createdAfter,
            LocalDate createdBefore,
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

        Specification<UserTenantMembership> spec =
                Specification.allOf(UserTenantMembershipSpecification.fetchUser(),
                                UserTenantMembershipSpecification.byTenant(tenantId))
                        .and(UserTenantMembershipSpecification.hasRole(role))
                        .and(UserTenantMembershipSpecification.hasStatus(status))
                        .and(UserTenantMembershipSpecification.search(normalizeSearch(search)))
                        .and(UserTenantMembershipSpecification.jobTitle(normalizePrefix(jobTitle)))
                        .and(UserTenantMembershipSpecification.department(normalizePrefix(department)))
                        .and(UserTenantMembershipSpecification.createdAfter(toStartOfDay(createdAfter)))
                        .and(UserTenantMembershipSpecification.createdBefore(toExclusiveEndOfDay(createdBefore)));

        Page<UserTenantMembership> memberships =
                membershipRepository.findAll(spec, pageable);

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

    private String normalizePrefix(String value) {
        if (value == null) {
            return null;
        }

        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase(Locale.ROOT) + "%";
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
        try {
            final String normalizedName = Tenant.normalizeName(name);
            requireUniqueTenantNameForCreate(normalizedName);

            final User actor = userService.getRequiredCurrentUser();
            final Tenant created = persistCreatedTenant(normalizedName, description, dataRegion, retentionDays, actor);

            ensureCreatorMembership(created, actor);

            publishTenantCreatedAuditAfterCommit(created.getId(), description);

            return created;
        } catch (RuntimeException ex) {
            publishTenantCreateFailedAudit(name, ex);
            throw ex;
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
            case "displayName" -> "user.displayName";
            case "jobTitle" -> "user.jobTitle";
            case "department" -> "user.department";
            case "role" -> "role";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
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

    private Instant toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant toExclusiveEndOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
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

    private void assignOwnerInvariant(Tenant tenant, User user) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(user, "user");

        // Always assign owner (creation + update)
        tenant.assignOwner(user);
    }

    @Nonnull
    private static Tenant buildTenant(String normalizedName, String description, String dataRegion, Integer
            retentionDays) {
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

    private String normalizeSearch(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.isBlank() ? null : v;
    }

    private Tenant persistCreatedTenant(
            String normalizedName,
            String description,
            String dataRegion,
            Integer retentionDays,
            User actor
    ) {
        try {
            Tenant tenant = buildTenant(normalizedName, description, dataRegion, retentionDays);
            tenant.assignOwner(actor);
            return tenantRepository.save(tenant);

        } catch (DataIntegrityViolationException ex) {
            if (isTenantNameUniqueViolation(ex)) {
                throw new TenantAlreadyExistsException("Tenant name already exists");
            }

            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_create_persist_failure operation=CREATE_TENANT normalizedName=" + normalizedName,
                    ex
            );
            throw ex;

        } catch (RuntimeException ex) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_create_persist_failure operation=CREATE_TENANT normalizedName=" + normalizedName,
                    ex
            );
            throw ex;
        }
    }

    private void ensureCreatorMembership(Tenant created, User actor) {
        try {
            tenantMembershipService.ensureMembership(
                    actor.getId(),
                    created.getId(),
                    TenantRole.MANAGER
            );
        } catch (RuntimeException ex) {
            InfraEventLogger.failure(
                    InfraEventType.DATABASE,
                    InfraEventActions.DB_ENTITY_PERSIST,
                    "tenant_create_membership_persist_failure",
                    ex
            );
            throw ex;
        }
    }

    private void publishTenantCreatedAuditAfterCommit(UUID tenantId, String description) {
        final String tenantIdValue = tenantId.toString();
        final TenantAuditMetadata metadata = new TenantAuditMetadata(
                tenantIdValue,
                "CREATE_TENANT",
                description
        );

        auditPublisher.publishAfterCommit(() -> {
            try {
                adminAuditEventService.record(
                        AdminAuditActionType.TENANT_CREATED,
                        tenantId,
                        tenantIdValue,
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
        });
    }

    private void publishTenantCreateFailedAudit(String name, RuntimeException failure) {
        final String subjectId = "TENANT_CREATE:" + (name == null ? "NULL" : name.trim());
        final String failureType = resolveCreateFailureType(failure);
        final TenantAuditMetadata metadata = new TenantAuditMetadata(
                null,
                "CREATE_TENANT",
                failureType
        );

        auditPublisher.publishNow(() -> {
            try {
                adminAuditEventService.record(
                        AdminAuditActionType.TENANT_CREATE_FAILED,
                        null,
                        subjectId,
                        null,
                        metadata
                );
            } catch (RuntimeException auditEx) {
                InfraEventLogger.failure(
                        InfraEventType.DATABASE,
                        InfraEventActions.DB_ENTITY_PERSIST,
                        "tenant_create_failed_audit_persist_failure",
                        auditEx
                );
            }
        });
    }
}