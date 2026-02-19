package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GOLD transport contract for cursor-based audit pagination.
 * Cursor is defined as (timestamp, id).
 */
public interface BaseAuditCursorPageDTO<T extends BaseAuditDTO> {

    List<T> items();

    boolean hasMore();

    Instant nextCursorTimestamp();

    UUID nextCursorId();
}
