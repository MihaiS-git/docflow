package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteSpecifications;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
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

        return page.map(invite -> new InviteAdminViewDTO(
                invite.getId(),
                invite.getEmail(),
                invite.getStatus(),
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                Duration.between(invite.getCreatedAt(), now).getSeconds(),
                invite.getTenantId(),
                null // tenantName optional if not joined
        ));
    }
}