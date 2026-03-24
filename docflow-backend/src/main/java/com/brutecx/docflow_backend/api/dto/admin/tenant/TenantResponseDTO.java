package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantType;
import com.brutecx.docflow_backend.domain.user.User;

import java.time.Instant;
import java.util.UUID;

public record TenantResponseDTO(
        UUID id,
        String name,
        String description,
        UUID ownerId,
        String ownerDisplayName,
        TenantStatus status,
        TenantType tenantType,
        String dataRegion,
        Integer retentionDays,
        boolean bootstrapEnabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantResponseDTO from(Tenant t) {
        User owner = t.getOwner();

        return new TenantResponseDTO(
                t.getId(),
                t.getName(),
                t.getDescription(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getDisplayName() : null,
                t.getStatus(),
                t.getTenantType(),
                t.getDataRegion(),
                t.getRetentionDays(),
                t.isBootstrapEnabled(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}