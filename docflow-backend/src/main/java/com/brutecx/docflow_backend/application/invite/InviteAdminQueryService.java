package com.brutecx.docflow_backend.application.invite;
import com.brutecx.docflow_backend.api.dto.invite.InviteAdminListItemDTO;
import com.brutecx.docflow_backend.domain.invite.Invite;
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
    public Page<InviteAdminListItemDTO> listInvites(Pageable pageable) {
        Instant now = Instant.now();

        return inviteRepository.findAll(pageable)
                .map(invite -> toDto(invite, now));
    }

    private static InviteAdminListItemDTO toDto(Invite invite, Instant now) {
        Instant createdAt = inviteTimestamp(invite);
        Instant expiresAt = inviteExpiresAt(invite);

        long ageSeconds = Duration.between(createdAt, now).getSeconds();

        return new InviteAdminListItemDTO(
                invite.getId(),
                invite.getEmail(),
                invite.getStatus(),
                createdAt,
                expiresAt,
                ageSeconds
        );
    }

    private static Instant inviteTimestamp(Invite invite) {
        return invite.getTimestamp();
    }

    private static Instant inviteExpiresAt(Invite invite) {
        return invite.getExpiresAt();
    }
}
