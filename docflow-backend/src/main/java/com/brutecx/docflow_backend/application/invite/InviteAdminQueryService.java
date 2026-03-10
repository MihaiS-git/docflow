package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteSpecifications;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteAdminQueryService {

    private final InviteRepository inviteRepository;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public Page<InviteAdminViewDTO> listInvites(
            UUID tenantId,
            String email,
            InviteStatus status,
            Pageable pageable
    ) {
        Specification<Invite> spec = Specification.allOf(
                InviteSpecifications.byTenant(tenantId),
                InviteSpecifications.emailStartsWith(email),
                InviteSpecifications.hasStatus(status)
        );

        Page<Invite> page = inviteRepository.findAll(spec, pageable);

        Instant now = Instant.now();

        Tenant tenant = tenantService.getRequired(tenantId);
        String tenantName = tenant.getName();

        return page.map(invite -> new InviteAdminViewDTO(
                invite.getId(),
                invite.getEmail(),
                invite.getFirstName(),
                invite.getLastName(),
                invite.getJobTitle(),
                invite.getDepartment(),
                invite.getTenantRole(),
                invite.getStatus(),
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                Duration.between(invite.getCreatedAt(), now).getSeconds(),
                invite.getTenantId(),
                tenantName
        ));
    }
}