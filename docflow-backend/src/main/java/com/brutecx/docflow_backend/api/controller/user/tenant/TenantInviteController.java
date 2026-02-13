package com.brutecx.docflow_backend.api.controller.user.tenant;

import com.brutecx.docflow_backend.api.dto.invite.CreateTenantInviteRequest;
import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tenant-scoped invite endpoints.
 * Authorization is enforced in SecurityConfig
 */
@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@RequestMapping("/api/tenants/{tenantId}/invites")
public class TenantInviteController {

    private final InviteApplicationService inviteApplicationService;

    @PostMapping
    public ResponseEntity<Void> invite(
            @PathVariable UUID tenantId,
            @RequestBody @Valid CreateTenantInviteRequest request
    ) {

        inviteApplicationService.createAndSendInvite(
                tenantId,
                request.email(),
                request.firstName(),
                request.lastName(),
                request.jobTitle(),
                request.department(),
                request.tenantRole()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{inviteId}/revoke")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID tenantId,
            @PathVariable UUID inviteId
    ) {
        inviteApplicationService.revokeInviteInTenant(inviteId, tenantId);
        return ResponseEntity.noContent().build();
    }
}