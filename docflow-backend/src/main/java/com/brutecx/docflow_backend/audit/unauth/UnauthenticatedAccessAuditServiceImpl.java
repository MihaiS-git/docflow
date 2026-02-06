package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnauthenticatedAccessAuditServiceImpl implements IUnauthenticatedAccessAuditService {

    private final UnauthenticatedAccessAuditEventRepository repository;

    @Override
    public void record(
            String requestId,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    ) {
        String fingerprint = EventFingerprint.of(List.of(
                "UNAUTHENTICATED",
                requestId,
                httpMethod,
                path,
                ip,
                userAgent
        ));

        try {
            repository.save(new UnauthenticatedAccessAuditEvent(
                    requestId,
                    httpMethod,
                    path,
                    ip,
                    userAgent,
                    fingerprint
            ));
        } catch (DataIntegrityViolationException e) {
            log.debug("Unauthenticated access audit deduped. requestId={} method={} path={}", requestId, httpMethod, path);
        }
    }
}
