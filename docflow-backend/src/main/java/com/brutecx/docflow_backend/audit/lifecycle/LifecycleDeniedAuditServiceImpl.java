package com.brutecx.docflow_backend.audit.lifecycle;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LifecycleDeniedAuditServiceImpl implements ILifecycleDeniedAuditService {

    private final LifecycleDeniedAuditEventRepository repository;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String correlationId,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {

        try {
            repository.save(new LifecycleDeniedAuditEvent(
                    correlationId,
                    subjectId,
                    reasonCode,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    eventFingerprint
            ));
        } catch (Exception ex) {
            log.error(
                    "LIFECYCLE AUDIT FAILURE. correlationId={} subjectId={} reasonCode={} path={}",
                    correlationId, subjectId, reasonCode, path, ex
            );
        }
    }
}
