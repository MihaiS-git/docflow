package com.brutecx.docflow_backend.audit.rbac;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditServiceImpl
        implements IRbacDeniedAuditService {

    private final RbacDeniedAuditEventRepository repository;
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
            repository.save(new RbacDeniedAuditEvent(
                    correlationId,
                    subjectId,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    eventFingerprint
            ));
        } catch (Exception ex) {
            log.error(
                    "RBAC AUDIT FAILURE. correlationId={} subjectId={} httpMethod={} fingerprint={}",
                    correlationId, subjectId, httpMethod + " " + path, eventFingerprint, ex
            );
        }
    }
}
