package com.brutecx.docflow_backend.api.controller.admin.invite;

import com.brutecx.docflow_backend.application.invite.CleanupResult;
import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import com.brutecx.docflow_backend.domain.invite.CreateInviteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/invites")
public class InviteController {

    private final InviteApplicationService inviteService;

    @PostMapping
    public ResponseEntity<Void> invite(
            @RequestBody @Valid CreateInviteRequest request
    ) {
        inviteService.createAndSendInvite(
                request.targetTenantId(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.jobTitle(),
                request.department()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revokeInvite(@PathVariable UUID id) {
        inviteService.revokeInvite(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cleanup/{targetTenantId}")
    public ResponseEntity<CleanupResult> cleanup(@PathVariable UUID targetTenantId) {
        return ResponseEntity.ok(
                inviteService.cleanupExpiredInvitesAndOrphanedUsers(targetTenantId)
        );
    }

}
