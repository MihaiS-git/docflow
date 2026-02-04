package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.api.error.UserNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByExternalSubjectId(String externalSubjectId);

    // required for invitation-only login reconciliation
    Optional<User> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    default User getRequired(UUID userId) {
        return findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));
    }

    boolean existsByTenantIdAndEmailIgnoreCase(UUID id, String normalizedEmail);

    Optional<User> findByEmail(String email);

    Optional<User> findByTenantIdAndStatus(UUID id, UserStatus userStatus);

    boolean existsByExternalSubjectIdIsNotNull();
}

