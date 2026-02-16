package com.brutecx.docflow_backend.api.controller.tenant;

import com.brutecx.docflow_backend.application.invite.CleanupResult;
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

    /**
     * Manual cleanup endpoint (tenant-scoped).
     * Only a tenant MANAGER may invoke.
     * Deletes:
     * - expired pending invites for the tenant
     * - orphaned LOCKED users that were never activated (no externalSubjectId)
     * Emits AdminAuditActionType.INVITE_CLEANUP via InviteApplicationService.
     */
    @PostMapping("/cleanup")
    public ResponseEntity<CleanupResult> cleanupExpiredInvitesAndOrphanedUsers(
            @PathVariable UUID tenantId
    ) {
        tenantRoleGuard.requireTenantManager(tenantId);

        CleanupResult result =
                inviteApplicationService.cleanupExpiredInvitesAndOrphanedUsers(tenantId);

        return ResponseEntity.ok(result);
    }
}
