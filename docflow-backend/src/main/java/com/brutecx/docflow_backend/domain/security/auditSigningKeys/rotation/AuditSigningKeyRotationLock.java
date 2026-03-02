package com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_signing_key_rotation_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSigningKeyRotationLock {

    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    public AuditSigningKeyRotationLock(Long id) {
        this.id = id;
    }
}