package com.brutecx.docflow_backend.audit.keyrotation;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditExportSigningKeyRotationAuditServiceImpl implements AuditExportSigningKeyRotationAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = AuditExportSigningKeyRotationCanonicalMaterialBuilder.STREAM;

    private final AuditExportSigningKeyRotationEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuditPartitionResolver partitionResolver;
    private final AuditExportSigningKeyRotationCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditCanonicalVersionProvider canonicalVersionProvider;
    private final CanonicalJsonService canonicalJsonService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRotation(
            AuditExportSigningKeyRotationReason reason,
            String oldKeyId,
            String newKeyId,
            String newFingerprintSha256Hex
    ) {

        ensureHttpContext();

        if (reason == null) throw new IllegalArgumentException("reason is required");
        if (newKeyId == null || newKeyId.isBlank()) throw new IllegalArgumentException("newKeyId is required");
        if (newFingerprintSha256Hex == null || newFingerprintSha256Hex.isBlank()) throw new IllegalArgumentException("newFingerprintSha256Hex is required");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        Instant ts = Instant.now();

        AuditExportSigningKeyRotationMetadata metadata =
                new AuditExportSigningKeyRotationMetadata(
                        reason,
                        oldKeyId,
                        newKeyId,
                        newFingerprintSha256Hex
                );

        String fingerprint = buildFingerprint(ts, correlationId, metadata);

        String canonicalMaterial = canonicalMaterialBuilder.buildCanonicalMaterial(
                new AuditExportSigningKeyRotationCanonicalMaterialBuilder.Input(
                        ts,
                        correlationId,
                        metadata,
                        fingerprint
                )
        );

        AuditPartition partition = partitionResolver.auditExportSigningKeyRotation();

        try {
            AuditChainService.ChainHash chain = auditChainService.nextHash(partition, canonicalMaterial);

            repository.save(new AuditExportSigningKeyRotationEvent(
                    ts,
                    correlationId,
                    metadata,
                    fingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (Exception ex) {
            log.error("AUDIT KEY ROTATION AUDIT FAILURE correlationId={} reason={} newKeyId={}",
                    correlationId, reason, newKeyId, ex);
            throw ex;
        }
    }

    private String buildFingerprint(
            Instant ts,
            String correlationId,
            AuditExportSigningKeyRotationMetadata metadata
    ) {
        int cv = canonicalVersionProvider.canonicalVersion();

        List<String> fp = new ArrayList<>();
        fp.add(STREAM);
        fp.add("CV=" + cv);
        fp.add("ts=" + ts.toEpochMilli());
        fp.add("correlationId=" + correlationId);
        fp.add("metadata=" + canonicalJsonService.toCanonicalJson(metadata));

        return EventFingerprint.of(fp);
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("Key rotation audit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for key rotation audit event");
        }
        return corr;
    }
}