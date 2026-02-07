package com.brutecx.docflow_backend.api.controller.admin.invite;

import com.brutecx.docflow_backend.api.dto.invite.InviteAdminListItemDTO;
import com.brutecx.docflow_backend.application.invite.InviteAdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/invites")
public class InviteAdminController {

    private final InviteAdminQueryService queryService;

    @GetMapping
    public ResponseEntity<Page<InviteAdminListItemDTO>> listInvites(
            @PageableDefault(
                    sort = "timestamp",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(queryService.listInvites(pageable));
    }
}
