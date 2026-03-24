package com.brutecx.docflow_backend.api.controller.admin.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.*;
import com.brutecx.docflow_backend.api.dto.tenant.TenantUserResponseDTO;
import com.brutecx.docflow_backend.domain.tenant.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
            "updatedAt"
    );

    private final TenantService tenantService;

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

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponseDTO> getById(
            @PathVariable UUID tenantId
    ) {
        Tenant tenant = tenantService.getRequiredWithOwner(tenantId);
        return ResponseEntity.ok(TenantResponseDTO.from(tenant));
    }

    @GetMapping("/{tenantId}/users")
    public ResponseEntity<Page<TenantUserResponseDTO>> listUsers(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) TenantRole role,
            @RequestParam(required = false) MembershipStatus status,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {

        Page<TenantUserResponseDTO> result =
                tenantService.listUsersByTenant(
                        tenantId,
                        role,
                        status,
                        page,
                        size,
                        sort,
                        direction
                );

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<TenantResponseDTO> create(
            @Valid @RequestBody CreateTenantRequest request
    ) {
        Tenant tenant = tenantService.create(
                request.name(),
                request.description()
        );
        return ResponseEntity.ok(TenantResponseDTO.from(tenant));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantRequest request
    ) {
        tenantService.updateTenant(
                tenantId,
                request.name(),
                request.description(),
                request.dataRegion(),
                request.retentionDays(),
                request.disableBootstrap(),
                request.comment()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable UUID tenantId,
            @RequestBody TenantRequest request
    ) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }
        tenantService.suspendTenant(tenantId, request.comment());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/reactivate")
    public ResponseEntity<Void> reactivate(
            @PathVariable UUID tenantId,
            @RequestBody TenantRequest request
    ) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }
        tenantService.reactivateTenant(tenantId, request.comment());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/terminate")
    public ResponseEntity<Void> terminate(
            @PathVariable UUID tenantId,
            @RequestBody TenantRequest request
    ) {

        if (request.comment() == null || request.comment().isBlank()) {
            throw new IllegalArgumentException("Comment required");
        }
        tenantService.terminateTenant(tenantId, request.comment());
        return ResponseEntity.noContent().build();
    }

}