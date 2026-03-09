package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.GenericAuditEventHashLocator;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKeyResolver;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditExportVerificationService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = "AUDIT_EXPORT_VERIFICATION";
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private static final String SIGNATURE_ALG = "SHA256withRSA";

    private final AuditExportSnapshotRepository snapshotRepository;
    private final AuditSigningKeyResolver keyResolver;
    private final AuditWriteFailureMetrics metrics;
    private final long maxUploadBytes;

    private final GenericAuditEventHashLocator hashLocator;
    private final JsonlMetaExtractor metaExtractor;
    private final StreamingVerifier streamingVerifier;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuditChainVerifier auditChainVerifier;

    @Value("${docflow.audit.verify.parallel.enabled:true}")
    private boolean parallelVerificationEnabled;

    @Value("${docflow.audit.verify.parallelism:4}")
    private int parallelVerificationParallelism;

    public AuditExportVerificationService(
            AuditExportSnapshotRepository snapshotRepository,
            AuditSigningKeyResolver keyResolver,
            AuditWriteFailureMetrics metrics,
            @Value("${docflow.audit.export.verify.max-bytes:268435456}") long maxUploadBytes,
            GenericAuditEventHashLocator hashLocator,
            JsonlMetaExtractor metaExtractor,
            StreamingVerifier streamingVerifier,
            AuditRequestContextExtractor contextExtractor,
            AuditChainVerifier auditChainVerifier
    ) {
        this.snapshotRepository = snapshotRepository;
        this.keyResolver = keyResolver;
        this.metrics = metrics;
        this.maxUploadBytes = maxUploadBytes;
        this.hashLocator = hashLocator;
        this.metaExtractor = metaExtractor;
        this.streamingVerifier = streamingVerifier;
        this.contextExtractor = contextExtractor;
        this.auditChainVerifier = auditChainVerifier;
    }

    @Transactional(readOnly = true)
    public AuditExportVerificationResultDTO verifySnapshotFile(UUID snapshotId, MultipartFile file) {

        final long startNs = System.nanoTime();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();

        String correlationId = ctx.correlationId() != null
                ? ctx.correlationId()
                : "verify-" + snapshotId;

        CorrelationSource correlationSource =
                ctx.correlationId() != null
                        ? CorrelationSource.REQUEST_ID
                        : CorrelationSource.GENERATED;

        AuditExportSnapshot snapshot;

        try {

            if (file == null || file.isEmpty()) {
                return fail(snapshotId, null, null,
                        "NO_FILE", "No file provided",
                        correlationId, correlationSource);
            }

            if (file.getSize() > maxUploadBytes) {
                return fail(snapshotId, null, null,
                        "FILE_TOO_LARGE", "File too large",
                        correlationId, correlationSource);
            }

            snapshot = snapshotRepository.findById(snapshotId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Snapshot not found: " + snapshotId));

            if (!SIGNATURE_ALG.equals(snapshot.getSignatureAlg())) {
                return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                        "UNSUPPORTED_SIGNATURE_ALG",
                        "Unsupported signature algorithm",
                        correlationId, correlationSource);
            }

            JsonlMetaExtractor.MetaValidationResult metaResult =
                    metaExtractor.extractAndValidateMeta(snapshot, file);

            AuditExportMetadataDTO meta = metaResult.meta();

            AuditSigningKey keyRow =
                    keyResolver.findById(snapshot.getKeyId()).orElse(null);

            if (keyRow == null) {
                return fail(
                        snapshot.getId(),
                        snapshot.getStream(),
                        snapshot.getTenantId(),
                        "VERIFY_KEY_MISSING",
                        "Signing key missing in DB",
                        correlationId,
                        correlationSource
                );
            }

            boolean fingerprintMatches =
                    meta.publicKeyFingerprint()
                            .equalsIgnoreCase(keyRow.getFingerprintSha256Hex());

            if (!fingerprintMatches) {
                return fail(
                        snapshot.getId(),
                        snapshot.getStream(),
                        snapshot.getTenantId(),
                        "VERIFY_KEY_FINGERPRINT_MISMATCH",
                        "Signing key fingerprint mismatch",
                        correlationId,
                        correlationSource
                );
            }

            PublicKey pk = keyResolver.requirePublicKey(snapshot.getKeyId());

            StreamingVerifier.StreamingVerifyResult streaming =
                    streamingVerifier.verify(snapshot, file, pk);

            boolean digestMatches =
                    streaming.computedDigestHex()
                            .equalsIgnoreCase(snapshot.getSha256DigestHex());

            boolean signatureValid = streaming.signatureValid();

            boolean rowCountMatches =
                    streaming.payloadRowCount() == snapshot.getRowCount();

            boolean ok = digestMatches && signatureValid && rowCountMatches;

            emitVerificationLog(
                    ok,
                    correlationId,
                    correlationSource,
                    snapshot.getId(),
                    snapshot.getStream(),
                    snapshot.getTenantId(),
                    ok ? "VERIFY_OK" : "VERIFY_FAILED",
                    ok ? "OK" : "FAILED",
                    kv("verify.digest_matches", digestMatches),
                    kv("verify.signature_valid", signatureValid),
                    kv("verify.key_resolved", true),
                    kv("verify.signature_input", meta.signatureInput()),
                    kv("verify.payload_length_bytes", streaming.payloadLengthBytes()),
                    kv("verify.payload_row_count", streaming.payloadRowCount())
            );

            if (ok) {
                metrics.incrementSuccess(STREAM, EXEC_CTX);
            } else {
                metrics.incrementFailure(STREAM, EXEC_CTX,
                        new IllegalStateException("Verification failed"));
            }

            return new AuditExportVerificationResultDTO(
                    ok,
                    snapshot.getId(),
                    snapshot.getStream(),
                    snapshot.getTenantId(),
                    snapshot.getFromTs(),
                    snapshot.getToTs(),
                    snapshot.getRowCount(),
                    snapshot.getSha256DigestHex(),
                    streaming.computedDigestHex(),
                    digestMatches,
                    true,
                    signatureValid,
                    snapshot.getSignatureAlg(),
                    snapshot.getKeyId(),
                    metaResult.metaPresent(),
                    metaResult.metaParsed(),
                    metaResult.metaSnapshotIdMatches(),
                    metaResult.metaDigestMatches(),
                    metaResult.metaSignatureMatches(),
                    metaResult.metaKeyIdMatches(),
                    metaResult.metaAlgorithmMatches(),
                    metaResult.metaDigestAlgorithmMatches(),
                    metaResult.metaSignatureInputMatches(),
                    metaResult.metaStreamMatches(),
                    metaResult.metaRangeMatches(),
                    metaResult.metaTenantMatches(),
                    metaResult.metaRowCountMatches(),
                    ok ? "OK" : "FAILED"
            );

        } catch (Exception e) {

            metrics.incrementFailure(STREAM, EXEC_CTX, e);

            throw new IllegalStateException("Verification failed", e);

        } finally {

            metrics.recordLatency(
                    STREAM,
                    EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs)
            );
        }
    }

    @Transactional(readOnly = true)
    public void verifyAllChains() {

        List<AuditPartition> partitions =
                hashLocator.listPartitionsForVerification();

        if (partitions.isEmpty()) {
            return;
        }

        if (!parallelVerificationEnabled || partitions.size() == 1) {

            for (AuditPartition partition : partitions) {
                assertVerified(partition);
            }

            return;
        }

        try (ForkJoinPool pool =
                     new ForkJoinPool(Math.max(1, parallelVerificationParallelism))) {

            pool.submit(() ->
                    partitions.parallelStream().forEach(this::assertVerified)).get();

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Parallel audit chain verification interrupted", ex);

        } catch (ExecutionException ex) {

            throw new IllegalStateException(
                    "Parallel audit chain verification failed", ex.getCause());
        }
    }

    private void assertVerified(AuditPartition partition) {

        AuditVerificationResultDTO result =
                auditChainVerifier.verifyPartitionChain(partition, null, null);

        if (!result.valid()) {

            throw new IllegalStateException(
                    "Audit chain verification failed for stream="
                            + partition.stream()
                            + " partition="
                            + partition.partitionValue()
                            + " reason="
                            + result.failureReason());
        }
    }

    private AuditExportVerificationResultDTO fail(
            UUID snapshotId,
            String stream,
            UUID tenantId,
            String action,
            String message,
            String correlationId,
            CorrelationSource source
    ) {

        emitVerificationLog(
                false,
                correlationId,
                source,
                snapshotId,
                stream,
                tenantId,
                action,
                message
        );

        metrics.incrementFailure(STREAM, EXEC_CTX,
                new IllegalStateException(message));

        return new AuditExportVerificationResultDTO(
                false,
                snapshotId,
                stream,
                tenantId,
                null,
                null,
                0L,
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                false,false,false,false,false,false,
                false,false,false,false,false,false,false,
                message
        );
    }

    private void emitVerificationLog(
            boolean ok,
            String correlationId,
            CorrelationSource correlationSource,
            UUID snapshotId,
            String snapshotStream,
            UUID tenantId,
            String action,
            String message,
            StructuredArgument... extra
    ) {

        StructuredArgument[] base = new StructuredArgument[]{
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", action),
                kv("event.outcome", ok ? "success" : "failure"),
                kv("audit.stream", STREAM),
                kv("execution.context", EXEC_CTX),
                kv("correlation.id", correlationId),
                kv("correlation.source", correlationSource.name()),
                kv("snapshot.id", snapshotId),
                kv("snapshot.stream", snapshotStream),
                kv("tenant.id", tenantId),
                kv("message", message)
        };

        StructuredArgument[] args =
                extra != null && extra.length > 0
                        ? Arrays.copyOf(base, base.length + extra.length)
                        : base;

        if (extra != null && extra.length > 0) {
            System.arraycopy(extra, 0, args, base.length, extra.length);
        }

        if (ok) {
            log.info("security_event {}", (Object[]) args);
        } else {
            log.error("security_event {}", (Object[]) args);
        }
    }
}