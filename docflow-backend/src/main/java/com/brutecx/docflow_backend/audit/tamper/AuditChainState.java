package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_chain_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditChainState {

    /**
     * Composite key encoded as: <stream>|<tenantId-or-NULL>
     */
    @Id
    @Column(name = "state_key", nullable = false, updatable = false, length = 256)
    private String stateKey;

    @Column(name = "stream", nullable = false, updatable = false, length = 128)
    private String stream;

    @Column(name = "tenant_id", nullable = true, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "last_event_hash", nullable = false, length = 128)
    private String lastEventHash;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuditChainState(String stateKey, String stream, String tenantId, String lastEventHash) {
        this.stateKey = stateKey;
        this.stream = stream;
        this.tenantId = tenantId;
        this.lastEventHash = lastEventHash;
        this.updatedAt = Instant.now();
    }

    public void updateLastHash(String lastEventHash) {
        this.lastEventHash = lastEventHash;
        this.updatedAt = Instant.now();
    }
}
