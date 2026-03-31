package com.brutecx.docflow_backend.api.controller.user.tenant;

import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseDTO;
import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@RequestMapping("/api/tenants/{tenantId}/users")
public class TenantUserController {

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<Page<TenantUserResponseDTO>> listUsers(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) TenantRole role,
            @RequestParam(required = false) MembershipStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        return ResponseEntity.ok(
                tenantService.listUsersByTenant(
                        tenantId,
                        role,
                        status,
                        search,
                        jobTitle,
                        department,
                        createdAfter,
                        createdBefore,
                        page,
                        size,
                        sort,
                        direction
                )
        );
    }
}
