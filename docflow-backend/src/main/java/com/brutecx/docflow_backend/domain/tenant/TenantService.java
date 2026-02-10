package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.TenantAuditMetadata;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final IAdminAuditEventService adminAuditEventService;

    @Transactional(readOnly = true)
    public TenantStatus getRequiredTenantStatus(UUID tenantId) {
        return tenantRepository.findStatusById(tenantId)
                .orElseThrow(() ->
                        new IllegalStateException("Tenant not found: " + tenantId)
                );
    }

    @Transactional(readOnly = true)
    public Tenant getSingleTenantForBootstrap() {
        long count = tenantRepository.count();

        if (count == 0) {
            throw new IllegalStateException(
                    "No tenant exists. Tenant bootstrap must run before admin bootstrap."
            );
        }

        if (count > 1) {
            throw new IllegalStateException(
                    "Multiple tenants exist (" + count + "). " +
                            "Admin bootstrap requires exactly one tenant."
            );
        }

        return tenantRepository.findAll().getFirst();
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

    @Transactional
    public Tenant create(String name) {
        if (tenantRepository.count() > 0) {
            throw new IllegalStateException(
                    "Bootstrap tenant creation is disabled once tenants exist"
            );
        }
        return tenantRepository.save(new Tenant(name));
    }

    /**
     * +     * Admin mutation: suspend tenant.
     * +     * Since there is no controller yet, caller must provide audit context explicitly.
     * +
     */
    @Transactional
    public void suspendTenant(
            UUID tenantId,
            String comment,
            String ip,
            String userAgent,
            String correlationId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");

        User actor = userService.getRequiredCurrentUser();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        try {
            tenant.suspend();
            tenantRepository.save(tenant);

            recordTenantAdminAudit(
                    actor,
                    ip,
                    userAgent,
                    correlationId,
                    tenant,
                    AdminAuditActionType.TENANT_SUSPENDED,
                    "SUSPEND_TENANT",
                    comment
            );
        } catch (TenantLifecycleViolationException ex) {
            recordTenantAdminAudit(
                    actor,
                    ip,
                    userAgent,
                    correlationId,
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "SUSPEND_TENANT",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Transactional
    public void reactivateTenant(
            UUID tenantId,
            String comment,
            String ip,
            String userAgent,
            String correlationId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");

        User actor = userService.getRequiredCurrentUser();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        try {
            tenant.reactivate();
            tenantRepository.save(tenant);

            recordTenantAdminAudit(
                    actor,
                    ip,
                    userAgent,
                    correlationId,
                    tenant,
                    AdminAuditActionType.TENANT_UPDATED,
                    "REACTIVATE_TENANT",
                    comment
            );
        } catch (TenantLifecycleViolationException ex) {
            recordTenantAdminAudit(
                    actor,
                    ip,
                    userAgent,
                    correlationId,
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "REACTIVATE_TENANT",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    /**
     * Admin mutation: update selected tenant fields.
     * Null values mean "no change" (not "clear").
     */
    @Transactional
    public void updateTenant(
            UUID tenantId,
            String newName,
            String newDataRegion,
            Long newRetentionDays,
            Boolean disableBootstrap,
            String comment,
            String ip,
            String userAgent,
            String correlationId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");

        User actor = userService.getRequiredCurrentUser();

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
                        actor,
                        ip,
                        userAgent,
                        correlationId,
                        tenant,
                        AdminAuditActionType.TENANT_UPDATED,
                        "UPDATE_TENANT",
                        comment
                );
            }
        } catch (TenantLifecycleViolationException ex) {
            recordTenantAdminAudit(
                    actor,
                    ip,
                    userAgent,
                    correlationId,
                    tenant,
                    AdminAuditActionType.TENANT_MUTATION_DENIED,
                    "UPDATE_TENANT",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private void recordTenantAdminAudit(
            User actor,
            String ip,
            String userAgent,
            String correlationId,
            Tenant tenant,
            AdminAuditActionType actionType,
            String operation,
            String comment
    ) {
        String subjectId = tenant.getId().toString();
        TenantAuditMetadata metadata = new TenantAuditMetadata(
                tenant.getId().toString(),
                operation,
                comment
        );

        String fingerprintMaterial = String.join("|",
                "TENANT_ADMIN",
                actor.getId().toString(),
                tenant.getId().toString(),
                actionType.name(),
                correlationId == null ? "" : correlationId,
                operation,
                comment == null ? "" : comment
        );

        adminAuditEventService.record(
                actor.getId(),
                ip,
                userAgent,
                correlationId,
                subjectId,
                tenant.getId(),
                actionType,
                null,
                metadata,
                sha256Hex(fingerprintMaterial)
        );
    }

    private static String buildOperationSummary(
            String newName,
            String newDataRegion,
            Long newRetentionDays,
            Boolean disableBootstrap
    ) {
        StringBuilder sb = new StringBuilder();
        if (newName != null) sb.append("name,");
        if (newDataRegion != null) sb.append("dataRegion,");
        if (newRetentionDays != null) sb.append("retentionDays,");
        if (Boolean.TRUE.equals(disableBootstrap)) sb.append("bootstrapDisabled,");
        if (sb.isEmpty()) return "none";
        sb.setLength(sb.length() - 1); // drop trailing comma
        return sb.toString();
    }

    private static String sha256Hex(String material) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
