package com.brutecx.docflow_backend.audit.tamper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuditChainService {

    private final AuditChainStateRepository stateRepository;
    private final AuditChainSecretProvider secretProvider;
    private final boolean enabled;

    public AuditChainService(
            AuditChainStateRepository stateRepository,
            AuditChainSecretProvider secretProvider,
            @Value("${docflow.audit.chain.enabled:true}") boolean enabled
    ) {
        this.stateRepository = stateRepository;
        this.secretProvider = secretProvider;
        this.enabled = enabled;
    }

    public record ChainHash(String prevHash, String eventHash, int chainVersion) {
    }

    public boolean isEnabledAndConfigured() {
        String secret = secretProvider.getResolvedSecretOrEmpty();
        return enabled && secret != null && !secret.isBlank();
    }

    /**
     * Pure hash computation used by verification (NO state read/write).
     * Must stay consistent with writer logic.
     */
    public String computeEventHash(
            AuditPartition partition,
            int chainVersion,
            String prevHash,
            String eventMaterial
    ) {
        if (!isEnabledAndConfigured()) {
            return "-";
        }

        if (chainVersion <= 0) {
            return "-";
        }

        String material = "v" + chainVersion
                + "|" + partition.stream()
                + "|" + partition.partitionValue()
                + "|" + prevHash
                + "|" + eventMaterial;

        String secret = secretProvider.getResolvedSecretOrEmpty();
        return AuditChainHasher.hmacSha256Hex(secret, material);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChainHash nextHash(AuditPartition partition, String eventMaterial) {

        if (!isEnabledAndConfigured()) {
            return new ChainHash("-", "-", 0);
        }

        String stateKey = partition.toStateKey();

        Optional<AuditChainState> lockedOpt = stateRepository.findForUpdate(stateKey);

        String prev = lockedOpt.map(AuditChainState::getLastEventHash).orElse("-");
        int version = 1;

        String eventHash = computeEventHash(partition, version, prev, eventMaterial);

        AuditChainState state = lockedOpt.orElseGet(() ->
                new AuditChainState(
                        stateKey,
                        partition.stream(),
                        partition.partitionValue(),
                        prev
                )
        );

        state.updateLastHash(eventHash);
        stateRepository.save(state);

        return new ChainHash(prev, eventHash, version);
    }
}
