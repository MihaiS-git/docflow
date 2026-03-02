package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditExportSnapshotQueryService {

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
            Instant cursorCreatedAt,
            UUID cursorId,
            int size
    ) {
        validateRange(createdFrom, createdTo);
        validateRange(exportFromTs, exportToTs);
        validateCursorPair(cursorCreatedAt, cursorId);

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

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Specification<AuditExportSnapshot> spec = Specification.allOf(
                hasText(stream) ? AuditExportSnapshotSpecifications.hasStream(stream) : null,
                tenantId != null ? AuditExportSnapshotSpecifications.hasTenantId(tenantId) : null,
                createdBy != null ? AuditExportSnapshotSpecifications.hasCreatedBy(createdBy) : null,
                createdFrom != null ? AuditExportSnapshotSpecifications.createdFrom(createdFrom) : null,
                createdTo != null ? AuditExportSnapshotSpecifications.createdTo(createdTo) : null,
                exportFromTs != null ? AuditExportSnapshotSpecifications.exportRangeFrom(exportFromTs) : null,
                exportToTs != null ? AuditExportSnapshotSpecifications.exportRangeTo(exportToTs) : null,
                cursorCreatedAt != null
                        ? AuditExportSnapshotSpecifications.cursorAfterDesc(cursorCreatedAt, cursorId)
                        : null
        );

        Page<AuditExportSnapshot> page = repository.findAll(spec, pageable);

        List<AuditExportSnapshot> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<AuditExportSnapshotDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(AuditExportSnapshotDTO.from(raw.get(i)));
        }

        Instant nextCursorCreatedAt = null;
        UUID nextCursorId = null;
        if (hasMore) {
            AuditExportSnapshot lastIncluded = raw.get(safeSize - 1);
            nextCursorCreatedAt = lastIncluded.getCreatedAt();
            nextCursorId = lastIncluded.getId();
        }

        return new AuditExportSnapshotCursorPageDTO(items, hasMore, nextCursorCreatedAt, nextCursorId);
    }

    @Transactional(readOnly = true)
    public AuditExportSnapshotDTO getById(UUID id) {
        AuditExportSnapshot s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + id));
        return AuditExportSnapshotDTO.from(s);
    }

    private static void validateCursorPair(Instant cursorTs, UUID cursorId) {
        if ((cursorTs == null) != (cursorId == null)) {
            throw new IllegalArgumentException("cursorCreatedAt and cursorId must be provided together");
        }
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
