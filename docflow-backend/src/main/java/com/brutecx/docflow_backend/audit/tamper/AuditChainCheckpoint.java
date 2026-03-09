package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_chain_checkpoints")
@Getter
@NoArgsConstructor
public class AuditChainCheckpoint {

    @Id
    @Column(name = "stream", nullable = false)
    private String stream;

    @Column(name = "last_event_timestamp", nullable = false)
    private Instant lastEventTimestamp;

    public AuditChainCheckpoint(String stream, Instant lastEventTimestamp) {
        this.stream = stream;
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public void update(Instant ts) {
        this.lastEventTimestamp = ts;
    }
}