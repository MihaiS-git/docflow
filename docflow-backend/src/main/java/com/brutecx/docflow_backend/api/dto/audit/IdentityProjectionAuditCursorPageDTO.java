package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdentityProjectionAuditCursorPageDTO(
        List<IdentityProjectionAuditDTO> content,
        boolean hasNext,
        Instant nextTimestamp,
        UUID nextId
) {
}
