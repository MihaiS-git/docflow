package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import com.brutecx.docflow_backend.api.error.ResourceNotFoundException;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.RsaKeyCodec;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.Optional;

@Service
public class AuditSigningKeyResolver {

    private final AuditSigningKeyRepository repository;

    public AuditSigningKeyResolver(AuditSigningKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<AuditSigningKey> findActive() {
        return repository.findFirstByActiveTrue();
    }

    @Transactional
    public Optional<AuditSigningKey> findById(String keyId) {
        if (keyId == null || keyId.isBlank()) return Optional.empty();
        return repository.findById(keyId.trim());
    }

    @Transactional
    public PublicKey requirePublicKey(String keyId) {

        if (keyId == null || keyId.isBlank())
            throw new IllegalArgumentException("keyId is required");

        AuditSigningKey row = repository.findById(keyId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Audit signing key not found"));

        return RsaKeyCodec.decodePublicKeyPem(row.getPublicKeyPem());
    }
}