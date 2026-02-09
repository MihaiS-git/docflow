package com.brutecx.docflow_backend.audit.tamper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuditChainService {

    private final AuditChainStateRepository stateRepository;
    private final String secret;
    private final boolean enabled;

    public AuditChainService(
            AuditChainStateRepository stateRepository,
            @Value("${docflow.audit.chain.secret:}") String secret,
            @Value("${docflow.audit.chain.enabled:true}") boolean enabled
    ) {
        this.stateRepository = stateRepository;
        this.secret = secret;
        this.enabled = enabled;
    }

    public record ChainHash(String prevHash, String eventHash, int chainVersion) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChainHash nextHash(String stream, String tenantIdOrNull, String eventMaterial) {
        if (!enabled || secret == null || secret.isBlank()) {
            return new ChainHash("-", "-", 0);
        }

        String tenantNorm = (tenantIdOrNull == null || tenantIdOrNull.isBlank()) ? "NULL" : tenantIdOrNull;
        String stateKey = stream + "|" + tenantNorm;

        Optional<AuditChainState> lockedOpt = stateRepository.findForUpdate(stateKey);

        String prev = lockedOpt.map(AuditChainState::getLastEventHash).orElse("-");
        int version = 1;

        String material = "v" + version + "|" + stream + "|" + tenantNorm + "|" + prev + "|" + eventMaterial;
        String eventHash = AuditChainHasher.hmacSha256Hex(secret, material);

        AuditChainState state = lockedOpt.orElseGet(() ->
                new AuditChainState(stateKey, stream, "NULL".equals(tenantNorm) ? null : tenantNorm, prev)
        );
        state.updateLastHash(eventHash);
        stateRepository.save(state);

        return new ChainHash(prev, eventHash, version);
    }
}
