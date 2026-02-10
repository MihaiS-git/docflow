package com.brutecx.docflow_backend.api.controller.admin.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/tenants")
public class AdminTenantController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TenantService tenantService;

    /* ---------------------------
     * Queries
     * --------------------------- */

    @GetMapping
    public ResponseEntity<Page<TenantListItemDTO>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(direction, sort)
        );

        return ResponseEntity.ok(
                tenantService.listAll(pageable)
                        .map(this::toDto)
        );
    }

    @GetMapping("/active")
    public ResponseEntity<Page<TenantListItemDTO>> listActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(direction, sort)
        );

        return ResponseEntity.ok(
                tenantService.listActive(pageable)
                        .map(this::toDto)
        );
    }

    /* ---------------------------
     * Mutations (unchanged)
     * --------------------------- */

    @PostMapping
    public ResponseEntity<Tenant> create(@RequestParam String name) {
        return ResponseEntity.ok(
                tenantService.create(name)
        );
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dataRegion,
            @RequestParam(required = false) Long retentionDays,
            @RequestParam(required = false) Boolean disableBootstrap,
            @RequestParam(required = false) String comment,
            HttpServletRequest request
    ) {
        tenantService.updateTenant(
                tenantId,
                name,
                dataRegion,
                retentionDays,
                disableBootstrap,
                comment,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Request-Id")
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable UUID tenantId,
            @RequestParam String comment,
            HttpServletRequest request
    ) {
        if (comment.isBlank()) {
            throw new IllegalArgumentException("Comment is required for tenant suspension");
        }

        tenantService.suspendTenant(
                tenantId,
                comment,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Request-Id")
        );

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{tenantId}/reactivate")
    public ResponseEntity<Void> reactivate(
            @PathVariable UUID tenantId,
            @RequestParam String comment,
            HttpServletRequest request
    ) {
        if (comment.isBlank()) {
            throw new IllegalArgumentException("Comment is required for tenant reactivation");
        }

        tenantService.reactivateTenant(
                tenantId,
                comment,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Request-Id")
        );

        return ResponseEntity.noContent().build();
    }

    private TenantListItemDTO toDto(Tenant t) {
        return new TenantListItemDTO(
                t.getId(),
                t.getName(),
                t.getStatus(),
                t.getDataRegion(),
                t.getRetentionDays(),
                t.isBootstrapEnabled(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

}
