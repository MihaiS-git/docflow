package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void record(
            String correlationId,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {

        try {
            Provenance provenance = resolveProvenance(correlationId, eventFingerprint);

            String material = String.join("|",
                    "UNAUTH",
                    provenance.executionContext.name(),
                    provenance.correlationId,
                    httpMethod,
                    path,
                    eventFingerprint
            );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            "UNAUTH",
                            provenance.correlationId,
                            material
                    );

            repository.save(new UnauthenticatedAccessAuditEvent(
                    provenance.correlationId,
                    provenance.correlationSource,
                    provenance.executionContext,
                    provenance.result,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    eventFingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (DataIntegrityViolationException e) {
            log.debug("Unauthenticated access audit deduped. correlationId={} method={} path={}",
                    correlationId, httpMethod, path
            );
        } catch (Exception ex) {
            log.error(
                    "UNAUTH AUDIT FAILURE. correlationId={} method={} path={}",
                    correlationId, httpMethod, path, ex
            );
        }
    }

    private Provenance resolveProvenance(String correlationId, String eventFingerprint) {
        ExecutionContext executionContext =
                (RequestContextHolder.getRequestAttributes() != null)
                        ? ExecutionContext.HTTP
                        : ExecutionContext.SYSTEM;

        // Unauthenticated access is a FAILED outcome by definition
        AuditResult result = AuditResult.FAILED;

        String mdcSource = MDC.get("correlationSource");
        if (correlationId != null && !correlationId.isBlank()) {
            CorrelationSource source =
                    "GENERATED".equalsIgnoreCase(mdcSource)
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;
            return new Provenance(correlationId, source, executionContext, result);
        }

        // No real correlation → deterministic generated id, explicitly labeled
        String generated = "gen-" + shortSha256(eventFingerprint != null ? eventFingerprint : "NO_FINGERPRINT");
        return new Provenance(generated, CorrelationSource.GENERATED, executionContext, result);
    }

    private static String shortSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }

    private record Provenance(
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result
    ) {
    }
}
