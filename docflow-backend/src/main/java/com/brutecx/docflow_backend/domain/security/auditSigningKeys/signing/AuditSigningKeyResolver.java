package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import com.brutecx.docflow_backend.api.error.ResourceNotFoundException;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.RsaKeyCodec;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditSigningKeyResolver {

    private final AuditSigningKeyRepository repository;

    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

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

        String trimmed = keyId.trim();

        PublicKey cached = publicKeyCache.get(trimmed);
        if (cached != null) return cached;

        AuditSigningKey row = repository.findById(trimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Audit signing key not found"));

        PublicKey pk = RsaKeyCodec.decodePublicKeyPem(row.getPublicKeyPem());
        PublicKey prev = publicKeyCache.putIfAbsent(trimmed, pk);
        return prev != null ? prev : pk;
    }

    @Transactional
    public boolean exists(String keyId) {
        if (keyId == null || keyId.isBlank()) return false;
        return repository.existsById(keyId.trim());
    }

    public void evict(String keyId) {
        if (keyId == null || keyId.isBlank()) return;
        publicKeyCache.remove(keyId.trim());
    }
}