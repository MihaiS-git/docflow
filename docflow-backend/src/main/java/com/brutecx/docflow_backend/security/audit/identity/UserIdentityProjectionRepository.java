package com.brutecx.docflow_backend.security.audit.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityProjectionRepository extends JpaRepository<UserIdentityProjection, String> {
}
