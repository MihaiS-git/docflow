package com.brutecx.docflow_backend.domain.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Profile("!test")
public class AuditSigningKeyRotationLockInitializer {

    private static final long LOCK_ID = 1L;

    private final AuditSigningKeyRotationLockRepository lockRepository;

    @Transactional
    public void ensureLockRowExists() {
        if (!lockRepository.existsById(LOCK_ID)) {
            lockRepository.save(new AuditSigningKeyRotationLock(LOCK_ID));
        }
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        ensureLockRowExists();
    }
}