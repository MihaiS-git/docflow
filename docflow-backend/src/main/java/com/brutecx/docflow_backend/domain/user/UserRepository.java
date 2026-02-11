package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.api.error.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, UUID>,
        JpaSpecificationExecutor<User> {
    Optional<User> findByExternalSubjectId(String externalSubjectId);

    Optional<User> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    Optional<User> findByEmail(String email);

    default User getRequired(UUID userId) {
        return findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));
    }

    boolean existsByTenantIdAndEmailIgnoreCase(UUID id, String normalizedEmail);


    List<User> findByStatusAndIdIn(UserStatus userStatus, List<UUID> userIds);

    boolean existsByTenantIdAndStatus(UUID tenantId, UserStatus status);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

}


