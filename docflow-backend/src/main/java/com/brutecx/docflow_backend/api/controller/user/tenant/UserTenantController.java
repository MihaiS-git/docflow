package com.brutecx.docflow_backend.api.controller.user.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO;
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
    public ResponseEntity<List<TenantLookupDTO>> listManagedTenants() {
        List<TenantLookupDTO> tenants =
                tenantService.listManagedTenantsForCurrentUser();

        return ResponseEntity.ok(tenants);
    }
}