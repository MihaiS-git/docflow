package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.BaseAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.PrintWriter;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractAuditStreamQueryService {

    protected record CursorQueryResult<T>(
            List<T> items,
            boolean hasMore,
            Instant nextCursorTimestamp,
            UUID nextCursorId
    ) {}

    @FunctionalInterface
    protected interface DescPageFetcher<E> {
        Page<E> fetch(Pageable pageable);
    }

    @FunctionalInterface
    protected interface VerifyBatchFetcher<E> {
        Page<E> fetch(Instant effectiveFrom, Instant cursorTimestamp, UUID cursorId, Pageable pageable);
    }

    protected <E, T> CursorQueryResult<T> executeCursorQuery(
            Instant from,
            Instant to,
            Instant cursorTimestamp,
            UUID cursorId,
            int size,
            int maxPageSize,
            boolean strictPageSize,
            DescPageFetcher<E> fetcher,
            Function<E, T> mapper,
            Function<E, Instant> timestampExtractor,
            Function<E, UUID> idExtractor
    ) {
        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

        int safeSize;
        if (strictPageSize) {
            if (size <= 0 || size > maxPageSize) {
                throw new IllegalArgumentException("size must be between 1 and " + maxPageSize);
            }
            safeSize = size;
        } else {
            safeSize = Math.min(Math.max(size, 1), maxPageSize);
        }

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Page<E> page = fetcher.fetch(pageable);

        List<E> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<T> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(mapper.apply(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            E lastIncluded = raw.get(safeSize - 1);
            nextTs = timestampExtractor.apply(lastIncluded);
            nextId = idExtractor.apply(lastIncluded);
        }

        return new CursorQueryResult<>(items, hasMore, nextTs, nextId);
    }

    protected <E> AuditVerificationResultDTO executeVerification(
            Instant from,
            Instant to,
            Instant checkpointStart,
            VerifyBatchFetcher<E> fetcher,
            Function<E, AuditPartition> partitionResolver,
            Function<E, String> canonicalMaterialExtractor,
            Function<E, UUID> idExtractor,
            Function<E, Instant> timestampExtractor,
            Function<E, Integer> chainVersionExtractor,
            Function<E, String> prevHashExtractor,
            Function<E, String> eventHashExtractor,
            AuditChainService auditChainService
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        Instant effectiveFrom = from;
        if (checkpointStart != null && checkpointStart.isAfter(from)) {
            effectiveFrom = checkpointStart;
        }

        Instant cursorTimestamp = null;
        UUID cursorId = null;
        long verified = 0;

        HashMap<String, String> lastHashByPartition = new HashMap<>();

        while (true) {
            Pageable pageable = PageRequest.of(
                    0,
                    AuditVerificationPolicy.VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<E> batch = fetcher.fetch(effectiveFrom, cursorTimestamp, cursorId, pageable);

            if (batch.isEmpty()) {
                return AuditVerificationResultDTO.success(verified);
            }

            for (E event : batch.getContent()) {
                AuditPartition partition = partitionResolver.apply(event);
                String partitionKey = partition.toStateKey();

                String canonical = canonicalMaterialExtractor.apply(event);

                String prevHash = AuditStreamSupport.normalizeHash(prevHashExtractor.apply(event));
                String storedHash = AuditStreamSupport.normalizeHash(eventHashExtractor.apply(event));

                String computedHash = auditChainService.computeEventHash(
                        partition,
                        chainVersionExtractor.apply(event),
                        prevHash,
                        canonical
                );

                if (!computedHash.equals(storedHash)) {
                    return AuditVerificationResultDTO.failure(
                            verified,
                            idExtractor.apply(event),
                            "HASH_MISMATCH"
                    );
                }

                String lastHash = lastHashByPartition.get(partitionKey);
                if (lastHash != null && !lastHash.equals(prevHash)) {
                    return AuditVerificationResultDTO.failure(
                            verified,
                            idExtractor.apply(event),
                            "CHAIN_CONTINUITY_BROKEN"
                    );
                }

                lastHashByPartition.put(partitionKey, storedHash);

                verified++;

                if (verified >= AuditVerificationPolicy.VERIFY_MAX_EVENTS) {
                    return AuditVerificationResultDTO.truncated(
                            verified,
                            timestampExtractor.apply(event),
                            idExtractor.apply(event)
                    );
                }

                cursorTimestamp = timestampExtractor.apply(event);
                cursorId = idExtractor.apply(event);
            }
        }
    }

    protected <E, T extends BaseAuditForensicExportDTO> void executeJsonlExport(
            OutputStream out,
            String stream,
            Instant from,
            Instant to,
            UUID tenantId,
            int exportMaxRows,
            SealedJsonlAuditExportService sealedJsonlAuditExportService,
            SealedJsonlAuditExportService.CursorBatchFetcher<E> fetcher,
            Function<E, T> mapper,
            Runnable afterSuccess
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                out,
                stream,
                from,
                to,
                tenantId,
                exportMaxRows,
                fetcher,
                mapper,
                afterSuccess
        );
    }

    protected <E> void executeCsvExport(
            HttpServletResponse response,
            String stream,
            Instant from,
            Instant to,
            int exportBatchSize,
            long exportMaxRows,
            DescPageFetcher<E> fetcher,
            AuditStreamSupport.ThrowingConsumer<PrintWriter> headerWriter,
            AuditStreamSupport.ThrowingBiConsumer<PrintWriter, E> rowWriter,
            Runnable afterSuccess
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        AuditStreamSupport.streamExportCsvAsc(
                response,
                stream,
                from,
                to,
                exportBatchSize,
                exportMaxRows,
                fetcher::fetch,
                headerWriter,
                rowWriter,
                afterSuccess
        );
    }
}