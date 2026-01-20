package com.brutecx.docflow_backend.security.audit.keycloak;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeycloakEventCheckpointRepository extends JpaRepository<KeycloakEventCheckpoint, String> {
}
