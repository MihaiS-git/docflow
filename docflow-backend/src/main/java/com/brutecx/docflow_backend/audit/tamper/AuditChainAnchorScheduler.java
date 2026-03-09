package com.brutecx.docflow_backend.audit.tamper;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditChainAnchorScheduler {

    private final AuditChainAnchorService anchorService;

    /**
     * Periodically persists audit chain anchors
     * outside the database to protect against
     * malicious DB tampering.
     */
    @Scheduled(cron = "${docflow.audit.anchor.cron:0 */30 * * * *}")
    public void snapshotAuditAnchors() {
        anchorService.snapshotAnchors();
    }
}