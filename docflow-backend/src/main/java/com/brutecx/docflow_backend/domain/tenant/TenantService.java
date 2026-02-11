package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.TenantAuditMetadata;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final IAdminAuditEventService adminAuditEventService;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<User> listUsersByTenant(UUID tenantId, Pageable pageable) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        Page<User> page = userService
                .getUserRepository()
                .findByTenantId(tenant.getId(), pageable);

        User actor = userService.getRequiredCurrentUser();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenant.getId(),
                SensitiveAccessSubjectType.USER,
                tenant.getId().toString(),
                "TENANT_USERS",
                "LIST",
                "/api/admin/tenants/" + tenantId + "/users",
                null,
                null,
                null,
                "ADMIN_LIST_TENANT_USERS",
                null,
                SensitiveDataClassification.CONFIDENTIAL,
                null
        );

        return page;
    }


    @Transactional(readOnly = true)
    public TenantStatus getRequiredTenantStatus(UUID tenantId) {
        return tenantRepository.findStatusById(tenantId)
                .orElseThrow(() ->
                        new IllegalStateException("Tenant not found: " + tenantId)
                );
    }

    @Transactional
    public Tenant getOrCreateBootstrapTenant() {

        return tenantRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    Tenant tenant = new Tenant("Brutecx");
                    return tenantRepository.save(tenant);
                });
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
                    success
                            ? comment
                            : failureType
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

}
