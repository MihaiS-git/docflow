package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAuditCursorPageDTO(
        List<AdminAuditDTO> items,
        boolean hasMore,
        Instant nextCursorTimestamp,
        UUID nextCursorId
) {
}
