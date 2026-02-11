package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InviteAdminQueryService {

    private final InviteRepository inviteRepository;

    @Transactional(readOnly = true)
    public Page<InviteAdminViewDTO> listInvites(Pageable pageable) {
        Page<InviteAdminViewDTO> page = inviteRepository.findAllWithTenant(pageable);
        Instant now = Instant.now();

        return page.map(dto -> new InviteAdminViewDTO(
                dto.id(),
                dto.email(),
                dto.status(),
                dto.createdAt(),
                dto.expiresAt(),
                Duration.between(dto.createdAt(), now).getSeconds(),
                dto.tenantId(),
                dto.tenantName()
        ));
    }

}
