package com.brutecx.docflow_backend.api.controller.admin.tenant;

import com.brutecx.docflow_backend.api.dto.tenant.UpdateMembershipRequest;
import com.brutecx.docflow_backend.domain.tenant.TenantMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/tenants/{tenantId}/users")
public class AdminTenantMembershipController {

    private final TenantMembershipService tenantMembershipService;

    @PatchMapping("/{userId}")
    public ResponseEntity<Void> updateMembership(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMembershipRequest request
    ) {
        tenantMembershipService.updateMembership(
                tenantId,
                userId,
                request.role(),
                request.status(),
                request.comment()
        );

        return ResponseEntity.noContent().build();
    }
}