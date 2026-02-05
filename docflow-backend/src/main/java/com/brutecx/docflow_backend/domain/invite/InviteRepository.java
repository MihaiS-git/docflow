package com.brutecx.docflow_backend.domain.invite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByStatusAndExpiresAtBefore(
            InviteStatus status,
            Instant now
    );
}
