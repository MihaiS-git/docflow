package com.brutecx.docflow_backend.audit.unauth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private final UnauthenticatedAccessAuditEventRepository repository;
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
            repository.save(new UnauthenticatedAccessAuditEvent(
                    correlationId,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    eventFingerprint
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
}
