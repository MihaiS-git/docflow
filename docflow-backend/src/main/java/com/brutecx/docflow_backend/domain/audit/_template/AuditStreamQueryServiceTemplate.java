package com.brutecx.docflow_backend.domain.audit._template;

import com.brutecx.docflow_backend.domain.audit.support.AuditStreamContract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AuditStreamQueryServiceTemplate {

    @Transactional(readOnly = true)
    public Object /* CursorPageDTO */ query(
            Instant from,
            Instant to,
            String correlationId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        AuditStreamContract.requireCursorPair(cursorTimestamp, cursorId);
        int safeSize = AuditStreamContract.safePageSize(size);

        // TODO:
        // - build Specification (from/to/correlationId/cursor)
        // - fetch safeSize+1 with Sort: timestamp DESC, id DESC
        // - map to DTO list
        // - compute hasMore / nextCursor
        // - recordSensitiveRead("READ", "...")

        return null;
    }

    @Transactional(readOnly = true)
    public Object /* VerificationResultDTO */ verify(Instant from, Instant to) {

        AuditStreamContract.requireRange(from, to);

        // TODO:
        // - iterate in batches with Sort: timestamp ASC, id ASC
        // - maintain per-partition lastHash map (or per-tenant / per-subject / global)
        // - continuity check: normalize(prev) == expectedPrev
        // - recompute eventHash from canonical material + auditChainService
        // - recordSensitiveRead("VERIFY", "...")

        return null;
    }

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            Instant from,
            Instant to,
            String correlationId,
            Consumer<Object /* ForensicExportDTO */> consumer
    ) {

        AuditStreamContract.requireRange(from, to);

        // TODO:
        // - iterate in batches with Sort: timestamp ASC, id ASC
        // - cursor paging (timestamp,id) ASC
        // - consumer.accept(ForensicDTO.from(event))
        // - enforce EXPORT_MAX_ROWS cap
        // - recordSensitiveRead("EXPORT", "...")
    }
}
