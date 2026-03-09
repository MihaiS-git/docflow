package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.tamper.AuditChainCheckpoint;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

public final class AuditStreamSupport {

    private static final long MAX_VERIFY_EVENTS = 5_000_000;

    private AuditStreamSupport() {
    }

    private static final DateTimeFormatter FILENAME_TS_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

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

    /* =====================================================
       ARCHIVAL SUPPORT (generic)
       ===================================================== */
    public static <T> Specification<T> notArchived() {
        return (root, query, cb) -> cb.isNull(root.get("archivedAt"));
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
    public static <E> AuditVerificationResultDTO verifyStream(
            Iterable<E> events,
            Function<E, UUID> idExtractor,
            Function<E, Instant> idTimestampExtractor,
            Function<E, AuditPartition> partitionResolver,
            Function<E, Integer> versionExtractor,
            Function<E, String> prevHashExtractor,
            Function<E, String> eventHashExtractor,
            Function<E, String> canonicalMaterialExtractor,
            AuditChainService auditChainService
    ) {

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        for (E e : events) {
            if (verified >= MAX_VERIFY_EVENTS) {
                return AuditVerificationResultDTO.truncated(
                        verified,
                        idTimestampExtractor.apply(e),
                        idExtractor.apply(e)
                );
            }

            AuditPartition partition = partitionResolver.apply(e);

            AuditVerificationResultDTO failure =
                    verifyEvent(
                            idExtractor.apply(e),
                            partition,
                            versionExtractor.apply(e),
                            prevHashExtractor.apply(e),
                            eventHashExtractor.apply(e),
                            canonicalMaterialExtractor.apply(e),
                            auditChainService,
                            lastHashByPartitionStateKey,
                            verified
                    );

            if (failure != null) {
                lastHashByPartitionStateKey.remove(partition.toStateKey());
                verified++;
                continue;
            }

            verified++;
        }

        return AuditVerificationResultDTO.success(verified);
    }

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

    /**
     * Existing low-level streamer. Does NOT set Content-Type / Content-Disposition.
     * Keep this stable to avoid breaking current callers.
     */
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

    /**
     * CSV export helper with standardized headers and filename:
     * <stream-slug>_from_<UTC>_to_<UTC>.csv
     * IMPORTANT: call validateRangeRequired(from,to) BEFORE calling this if needed by your contract.
     */
    public static <E> void streamExportCsvAsc(
            HttpServletResponse response,
            String stream,
            Instant from,
            Instant to,
            int batchSize,
            long maxRows,
            Function<Pageable, Page<E>> pageSupplier,
            ThrowingConsumer<PrintWriter> beforeRows,
            ThrowingBiConsumer<PrintWriter, E> rowWriter,
            Runnable onComplete
    ) {

        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("stream required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to required");
        }

        // Align with JSONL naming strategy (auditor-friendly).
        String filename =
                toSlug(stream) +
                        "_from_" + formatInstantForFilename(from) +
                        "_to_" + formatInstantForFilename(to) +
                        ".csv";

        // Set headers here so callers don't need to replicate this logic.
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\""
        );

        streamExportAsc(
                response,
                batchSize,
                maxRows,
                pageSupplier,
                beforeRows,
                rowWriter,
                onComplete
        );
    }

    private static String formatInstantForFilename(Instant ts) {
        return FILENAME_TS_UTC.format(ts);
    }

    /**
     * Converts STREAM constant (e.g. ADMIN_ACTIONS) into kebab-case (admin-actions).
     * This matches the naming scheme chosen for audit evidence files.
     */
    private static String toSlug(String stream) {
        String lower = stream.toLowerCase(Locale.ROOT);
        return lower.replace('_', '-');
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

    /* =====================================================
       HELPERS
       ===================================================== */
    public static Instant resolveCheckpointStart(
            Optional<AuditChainCheckpoint> checkpoint
    ) {
        return checkpoint
                .map(AuditChainCheckpoint::getLastEventTimestamp)
                .orElse(null);
    }
}