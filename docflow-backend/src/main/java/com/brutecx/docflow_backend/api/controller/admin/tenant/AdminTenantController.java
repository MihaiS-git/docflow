package com.brutecx.docflow_backend.api.controller.admin.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantFilter;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO;
import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO;
import com.brutecx.docflow_backend.domain.tenant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/tenants")
public class AdminTenantController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "status",
            "dataRegion",
            "retentionDays",
            "createdAt",
            "updatedAt"
    );

    private final TenantService tenantService;

    @GetMapping("/lookup")
    public ResponseEntity<List<TenantLookupDTO>> lookupTenants() {
        return ResponseEntity.ok(tenantService.listTenantLookup());
    }

    @GetMapping
    public ResponseEntity<Page<TenantListItemDTO>> listAll(

            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dataRegion,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String managerEmail,
            @RequestParam(required = false) LocalDate createdAfter,
            @RequestParam(required = false) LocalDate createdBefore,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {

        String safeSort = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "createdAt";

        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(direction, safeSort)
        );

        TenantFilter filter = new TenantFilter(
                status,
                name,
                dataRegion,
                managerName,
                managerEmail,
                createdAfter,
                createdBefore
        );

        return ResponseEntity.ok(
                tenantService.listAll(filter, pageable)
        );
    }

    @PostMapping
    public ResponseEntity<Tenant> create(
            @RequestParam String name,
            @RequestParam(required = false) String comment
    ) {
        return ResponseEntity.ok(
                tenantService.create(name, comment)
        );
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dataRegion,
            @RequestParam(required = false) Long retentionDays,
            @RequestParam(required = false) Boolean disableBootstrap,
            @RequestParam(required = false) String comment
    ) {

        tenantService.updateTenant(
                tenantId,
                name,
                dataRegion,
                retentionDays,
                disableBootstrap,
                comment
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable UUID tenantId,
            @RequestParam String comment
    ) {

        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }

        tenantService.suspendTenant(tenantId, comment);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/reactivate")
    public ResponseEntity<Void> reactivate(
            @PathVariable UUID tenantId,
            @RequestParam String comment
    ) {

        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }

        tenantService.reactivateTenant(tenantId, comment);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/terminate")
    public ResponseEntity<Void> terminate(
            @PathVariable UUID tenantId,
            @RequestParam String comment
    ) {

        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }

        tenantService.terminateTenant(tenantId, comment);

        return ResponseEntity.noContent().build();
    }

}