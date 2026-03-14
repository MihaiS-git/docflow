package com.brutecx.docflow_backend.api.controller.user.tenant;

import com.brutecx.docflow_backend.api.dto.tenant.UserTenantListItemDTO;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@RequestMapping("/api/tenants")
public class UserTenantController {

    private final TenantService tenantService;

    @GetMapping("/managed")
    public ResponseEntity<List<UserTenantListItemDTO>> listManagedTenants() {
        List<Tenant> tenants =
                tenantService.listManagedTenantsForCurrentUser();

        return ResponseEntity.ok(
                tenants.stream()
                        .map(this::toDto)
                        .toList()
        );
    }

    private UserTenantListItemDTO toDto(Tenant t) {
        return new UserTenantListItemDTO(
                t.getId(),
                t.getName(),
                t.getStatus(),
                t.getDataRegion(),
                t.getRetentionDays(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}