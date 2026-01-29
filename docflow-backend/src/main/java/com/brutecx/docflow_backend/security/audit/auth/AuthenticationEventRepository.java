package com.brutecx.docflow_backend.security.audit.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthenticationEventRepository extends JpaRepository<AuthenticationEvent, UUID> {
}
