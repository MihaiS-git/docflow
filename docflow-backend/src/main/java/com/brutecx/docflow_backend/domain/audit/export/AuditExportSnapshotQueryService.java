package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotDTO;
import com.brutecx.docflow_backend.domain.audit.AbstractAuditStreamQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditExportSnapshotQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditExportSnapshotRepository repository;

    @Transactional(readOnly = true)
    public AuditExportSnapshotCursorPageDTO query(
            UUID snapshotId,
            String stream,
            UUID tenantId,
            UUID createdBy,
            Instant createdFrom,
            Instant createdTo,
            Instant exportFromTs,
            Instant exportToTs,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {
        validateRange(createdFrom, createdTo);
        validateRange(exportFromTs, exportToTs);

        // ✅ Fast path: exact lookup (no pagination semantics)
        if (snapshotId != null) {
            AuditExportSnapshot s = repository.findById(snapshotId)
                    .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

            return new AuditExportSnapshotCursorPageDTO(
                    List.of(AuditExportSnapshotDTO.from(s)),
                    false,
                    null,
                    null
            );
        }

        CursorQueryResult<AuditExportSnapshotDTO> result = executeCursorQuery(
                createdFrom,
                createdTo,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                hasText(stream) ? AuditExportSnapshotSpecifications.hasStream(stream) : null,
                                tenantId != null ? AuditExportSnapshotSpecifications.hasTenantId(tenantId) : null,
                                createdBy != null ? AuditExportSnapshotSpecifications.hasCreatedBy(createdBy) : null,
                                createdFrom != null ? AuditExportSnapshotSpecifications.createdFrom(createdFrom) : null,
                                createdTo != null ? AuditExportSnapshotSpecifications.createdTo(createdTo) : null,
                                exportFromTs != null ? AuditExportSnapshotSpecifications.exportRangeFrom(exportFromTs) : null,
                                exportToTs != null ? AuditExportSnapshotSpecifications.exportRangeTo(exportToTs) : null,
                                cursorTimestamp != null
                                        ? AuditExportSnapshotSpecifications.cursorAfterDesc(cursorTimestamp, cursorId)
                                        : null
                        ),
                        pageable // ✅ now safe because entity uses "timestamp"
                ),
                AuditExportSnapshotDTO::from,
                AuditExportSnapshot::getTimestamp,
                AuditExportSnapshot::getId
        );

        return new AuditExportSnapshotCursorPageDTO(
                result.items(),
                result.hasMore(),
                result.nextCursorTimestamp(),
                result.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditExportSnapshotDTO getById(UUID id) {
        AuditExportSnapshot s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + id));

        return AuditExportSnapshotDTO.from(s);
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid range: from > to");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}