package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UnauthenticatedAccessAuditCursorPageDTO(
        List<UnauthenticatedAccessAuditDTO> items,
        boolean hasMore,
        Instant nextCursorTimestamp,
        UUID nextCursorId
) implements BaseAuditCursorPageDTO<UnauthenticatedAccessAuditDTO> {}
