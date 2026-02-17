package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.*;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class GoldAuditSupport {

    private GoldAuditSupport() {}

    /* =====================================================
       VALIDATION
       ===================================================== */

    public static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
    }

    public static void validateRangeRequired(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        validateRange(from, to);
    }

    public static void validateCursorPair(Instant ts, UUID id) {
        if ((ts == null) ^ (id == null)) {
            throw new IllegalArgumentException("cursorTimestamp and cursorId must be provided together");
        }
    }

    public static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    public static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuotes =
                s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /* =====================================================
       VERIFY CONTINUITY + HASH
       ===================================================== */

    public static AuditVerificationResultDTO verifyEvent(
            java.util.UUID eventId,
            AuditPartition partition,
            int chainVersion,
            String prevEventHash,
            String eventHash,
            String canonicalMaterial,
            AuditChainService auditChainService,
            Map<String, String> lastHashByPartitionStateKey,
            long verifiedSoFar
    ) {

        String stateKey = partition.toStateKey();

        String actualPrev = normalizeHash(prevEventHash);
        String storedHash = normalizeHash(eventHash);

        String expectedPrev = lastHashByPartitionStateKey.get(stateKey);

        if (expectedPrev != null && !Objects.equals(expectedPrev, actualPrev)) {
            return AuditVerificationResultDTO.failure(
                    verifiedSoFar,
                    eventId,
                    "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
            );
        }

        if (chainVersion > 0) {
            String expectedHash = auditChainService.computeEventHash(
                    partition,
                    chainVersion,
                    actualPrev,
                    canonicalMaterial
            );

            if (!Objects.equals(expectedHash, storedHash)) {
                return AuditVerificationResultDTO.failure(
                        verifiedSoFar,
                        eventId,
                        "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED partition=" + partition.partitionValue()
                );
            }
        }

        lastHashByPartitionStateKey.put(stateKey, storedHash);
        return null;
    }

    /* =====================================================
       EXPORT STREAM TEMPLATE (ASC)
       ===================================================== */

    public static <E> void streamExportAsc(
            HttpServletResponse response,
            int batchSize,
            long maxRows,
            Function<Pageable, Page<E>> pageSupplier,
            ThrowingConsumer<PrintWriter> beforeRows,
            ThrowingBiConsumer<PrintWriter, E> rowWriter,
            Runnable onComplete
    ) {

        long exported = 0;

        response.setBufferSize(16 * 1024);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            if (beforeRows != null) {
                beforeRows.accept(w);
                w.flush();
                response.flushBuffer();
            }

            Pageable pageable = PageRequest.of(
                    0,
                    batchSize,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            while (true) {

                Page<E> page = pageSupplier.apply(pageable);
                if (page.isEmpty()) break;

                for (E e : page.getContent()) {

                    try {
                        rowWriter.accept(w, e);
                    } catch (Exception ex) {
                        if (!response.isCommitted()) {
                            response.resetBuffer();
                        }
                        throw ex;
                    }

                    exported++;
                    if (exported >= maxRows) {
                        w.flush();
                        response.flushBuffer();
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                }

                w.flush();
                response.flushBuffer();

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            if (onComplete != null) onComplete.run();
            w.flush();
            response.flushBuffer();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream audit export", ex);
        }
    }

    /* =====================================================
       FUNCTIONAL INTERFACES
       ===================================================== */

    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
