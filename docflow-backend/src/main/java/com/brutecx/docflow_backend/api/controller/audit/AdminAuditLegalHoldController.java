package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditLegalHoldDTO;
import com.brutecx.docflow_backend.api.dto.audit.CreateLegalHoldRequestDTO;
import com.brutecx.docflow_backend.domain.audit.retention.*;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit/legal-holds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLegalHoldController {

    private final AuditLegalHoldService legalHoldService;
    private final AuditLegalHoldQueryService queryService;
    private final UserService userService;
    private final TenantService tenantService;

    private static final int MAX_PAGE_SIZE = 200;

    @GetMapping
    public Page<AuditLegalHoldDTO> list(
            @RequestParam(required = false) String streamName,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String caseReferenceId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return queryService
                .query(
                        streamName,
                        active,
                        eventId,
                        correlationId,
                        caseReferenceId,
                        createdBy,
                        pageable
                )
                .map(this::mapToDto);
    }

    @PostMapping
    public AuditLegalHold create(@RequestBody CreateLegalHoldRequestDTO request) {

        User actor = userService.getRequiredCurrentUser();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        return legalHoldService.createHold(
                request.streamName(),
                request.eventId(),
                request.correlationId(),
                request.caseReferenceId(),
                request.reason(),
                rootTenantId,
                actor.getId()
        );
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable UUID id) {

        User actor = userService.getRequiredCurrentUser();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        legalHoldService.deactivate(
                id,
                rootTenantId,
                actor.getId()
        );
    }

    private AuditLegalHoldDTO mapToDto(AuditLegalHold h) {
        return new AuditLegalHoldDTO(
                h.getId(),
                h.getStreamName(),
                h.getEventId(),
                h.getCorrelationId(),
                h.getCaseReferenceId(),
                h.getReason(),
                h.getCreatedBy(),
                h.getCreatedAt(),
                h.isActive()
        );
    }
}