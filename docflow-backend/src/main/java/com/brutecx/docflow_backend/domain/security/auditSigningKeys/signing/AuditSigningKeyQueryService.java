package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import com.brutecx.docflow_backend.api.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditSigningKeyQueryService {

    private final AuditSigningKeyRepository repository;

    public List<AuditSigningKeyPublicDTO> listPublicKeys() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(AuditSigningKey::getCreatedAt).reversed())
                .map(AuditSigningKeyPublicDTO::from)
                .toList();
    }

    public AuditSigningKey getRequired(String keyId) {
        return repository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit signing key not found."));
    }
}