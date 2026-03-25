package com.brutecx.docflow_backend.api.controller.tenant;

import com.brutecx.docflow_backend.application.invite.ExpireInvitesResult;
import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import com.brutecx.docflow_backend.domain.tenant.TenantRoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/tenants/{tenantId}/invites")
@PreAuthorize("isAuthenticated()")
public class TenantInviteMaintenanceController {

    private final InviteApplicationService inviteApplicationService;
    private final TenantRoleGuard tenantRoleGuard;

    @PostMapping("/expire")
    public ResponseEntity<ExpireInvitesResult> expirePendingInvites(
            @PathVariable UUID tenantId
    ) {
        tenantRoleGuard.requireTenantManager(tenantId);

        ExpireInvitesResult result =
                inviteApplicationService.expirePendingInvites(tenantId);

        return ResponseEntity.ok(result);
    }
}
