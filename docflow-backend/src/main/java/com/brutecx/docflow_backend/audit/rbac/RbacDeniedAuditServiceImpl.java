package com.brutecx.docflow_backend.audit.rbac;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditServiceImpl
        implements IRbacDeniedAuditService {

    private final RbacDeniedAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    @Transactional
    public void record(
            String correlationId,
            String subjectId,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {

        try {
            Provenance provenance = resolveProvenance(correlationId, eventFingerprint);

            String material = String.join("|",
                    "RBAC_DENIED",
                    provenance.executionContext.name(),
                    provenance.correlationId,
                    subjectId != null ? subjectId : "-",
                    httpMethod,
                    path,
                    eventFingerprint
            );

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            "RBAC_DENIED",
                            provenance.correlationId,
                            material
                    );

            repository.save(new RbacDeniedAuditEvent(
                    provenance.correlationId,
                    provenance.correlationSource,
                    provenance.executionContext,
                    provenance.result,
                    subjectId,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    eventFingerprint,
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));
        } catch (Exception ex) {
            log.error(
                    "RBAC AUDIT FAILURE. correlationId={} subjectId={} httpMethod={} fingerprint={}",
                    correlationId, subjectId, httpMethod + " " + path, eventFingerprint, ex
            );
        }
    }

    private Provenance resolveProvenance(String correlationId, String eventFingerprint) {
        ExecutionContext executionContext =
                (RequestContextHolder.getRequestAttributes() != null)
                        ? ExecutionContext.HTTP
                        : ExecutionContext.SYSTEM;

        // This table is specifically for "denied" actions.
        AuditResult result = AuditResult.DENIED;

        String mdcSource = MDC.get("correlationSource");
        if (correlationId != null && !correlationId.isBlank()) {
            CorrelationSource source =
                    "GENERATED".equalsIgnoreCase(mdcSource)
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;
            return new Provenance(correlationId, source, executionContext, result);
        }

        // No real correlation available → deterministic generated id, explicitly labeled.
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
