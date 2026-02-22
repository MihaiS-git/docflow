package com.brutecx.docflow_backend.domain.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditSigningKeyQueryService {

    private final AuditSigningKeyRepository repository;

    public List<AuditSigningKeyPublicDTO> listPublicKeys() {
        return repository.findAll().stream()
                .map(AuditSigningKeyPublicDTO::from)
                .toList();
    }

    public AuditSigningKey getRequired(String keyId) {
        return repository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Audit signing key not found: " + keyId));
    }
}