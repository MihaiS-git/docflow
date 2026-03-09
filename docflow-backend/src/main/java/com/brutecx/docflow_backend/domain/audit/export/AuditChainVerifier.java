package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.GenericAuditEventHashLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
public class AuditChainVerifier {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuditChainService auditChainService;
    private final GenericAuditEventHashLocator hashLocator;

    @Value("${docflow.audit.verify.pre-scan.limit:50000}")
    private int continuityPreScanLimit;

    public AuditChainVerifier(AuditChainService auditChainService,
                              GenericAuditEventHashLocator hashLocator
    ) {
        this.auditChainService = auditChainService;
        this.hashLocator = hashLocator;
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verifyPartitionChain(
            AuditPartition partition,
            Instant cursorTimestamp,
            UUID cursorId
    ) {
        long verified = 0;

        String expectedPrevHash;
        Instant scanCursorTimestamp;
        UUID scanCursorId;

        if (cursorTimestamp != null || cursorId != null) {
            expectedPrevHash =
                    hashLocator.findEventHashByCursor(partition, cursorId).orElse("-");

            scanCursorTimestamp = cursorTimestamp;
            scanCursorId = cursorId;
        } else {
            AuditChainService.VerificationAnchor anchor =
                    auditChainService.resolveVerificationAnchor(partition);

            if (anchor.cursorId() == null) {
                expectedPrevHash = "-";
                scanCursorTimestamp = null;
                scanCursorId = null;
            } else {
                Optional<String> anchorHash =
                        hashLocator.findEventHashByCursor(partition, anchor.cursorId());

                if (anchorHash.isEmpty() || !anchorHash.get().equals(anchor.expectedPrevHash())) {
                    throw new IllegalStateException(
                            "Audit chain checkpoint mismatch for stream="
                                    + partition.stream()
                                    + " partition="
                                    + partition.partitionValue()
                    );
                }

                expectedPrevHash = anchor.expectedPrevHash();
                scanCursorTimestamp = anchor.cursorTimestamp();
                scanCursorId = anchor.cursorId();
            }
        }

        Optional<GenericAuditEventHashLocator.ChainBreak> preScanBreak =
                hashLocator.findFirstContinuityBreak(
                        partition,
                        expectedPrevHash,
                        scanCursorTimestamp,
                        scanCursorId,
                        Math.max(continuityPreScanLimit, 1)
                );

        if (preScanBreak.isPresent()) {
            GenericAuditEventHashLocator.ChainBreak broken = preScanBreak.get();

            scanCursorTimestamp = broken.timestamp();
            scanCursorId = broken.id();

            expectedPrevHash =
                    hashLocator.findEventHashByCursor(partition, scanCursorId).orElse("-");

            log.warn("security_event {}",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "audit_chain_segment_resume"),
                    kv("audit.stream", partition.stream()),
                    kv("audit.partition", partition.partitionValue()),
                    kv("audit.resume_event_id", scanCursorId)
            );
        }

        /*
         * SEGMENT ANCHOR JUMP
         */
        Optional<GenericAuditEventHashLocator.VerificationCursor> segment =
                hashLocator.findLastSegmentCursor(partition, scanCursorTimestamp);

        if (segment.isPresent()) {
            GenericAuditEventHashLocator.VerificationCursor seg = segment.get();

            scanCursorTimestamp = seg.timestamp();
            scanCursorId = seg.id();
            expectedPrevHash = normalizeHash(seg.eventHash());
        }

        boolean ok = hashLocator.verifyTailContinuity(
                partition,
                scanCursorTimestamp,
                scanCursorId,
                expectedPrevHash
        );

        if (!ok) {
            return new AuditVerificationResultDTO(
                    false,
                    verified,
                    null,
                    "CHAIN_BREAK",
                    scanCursorTimestamp,
                    scanCursorId
            );
        }

        return AuditVerificationResultDTO.success(verified);
    }

    private String normalizeHash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}