package com.brutecx.docflow_backend.audit.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuthenticationEventRepository
        extends JpaRepository<AuthenticationEvent, UUID>,
        JpaSpecificationExecutor<AuthenticationEvent> {

}
