package com.brutecx.docflow_backend.api.controller.user.tenant;

import com.brutecx.docflow_backend.api.dto.common.PageDTO;
import com.brutecx.docflow_backend.api.dto.invite.CreateTenantInviteRequest;
import com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO;
import com.brutecx.docflow_backend.api.dto.invite.InviteQueryRequest;
import com.brutecx.docflow_backend.application.invite.InviteAdminQueryService;
import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@RequestMapping("/api/tenants/{tenantId}/invites")
public class TenantInviteController {

    private final InviteApplicationService inviteApplicationService;
    private final InviteAdminQueryService inviteAdminQueryService;

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

    @GetMapping
    public ResponseEntity<PageDTO<InviteAdminViewDTO>> listInvites(
            @PathVariable UUID tenantId,
            @Valid InviteQueryRequest query
    ) {

        Sort sort = Sort.by(
                query.resolvedSortDir() == InviteQueryRequest.SortDirection.ASC
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                mapSortField(query.resolvedSortField())
        );

        PageRequest pageRequest =
                PageRequest.of(query.resolvedPage(), query.resolvedSize(), sort);

        return ResponseEntity.ok(
                PageDTO.from(
                        inviteAdminQueryService.listInvites(
                                tenantId,
                                query.email(),
                                query.status(),
                                pageRequest
                        )
                )
        );
    }

    private String mapSortField(InviteQueryRequest.SortField field) {
        return switch (field) {
            case CREATED_AT -> "createdAt";
            case EXPIRES_AT -> "expiresAt";
            case EMAIL -> "email";
            case STATUS -> "status";
        };
    }
}