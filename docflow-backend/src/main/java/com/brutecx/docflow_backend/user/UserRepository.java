package com.brutecx.docflow_backend.user;

import com.brutecx.docflow_backend.api.error.UserNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByExternalSubjectId(String externalSubjectId);

    default User getRequired(UUID userId) {
        return findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));
    }
}

