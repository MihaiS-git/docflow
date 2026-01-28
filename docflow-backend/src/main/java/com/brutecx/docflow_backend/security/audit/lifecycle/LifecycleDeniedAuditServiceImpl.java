package com.brutecx.docflow_backend.security.audit.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LifecycleDeniedAuditServiceImpl implements ILifecycleDeniedAuditService {

    private final LifecycleDeniedAuditEventRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String requestId,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    ) {
        LifecycleDeniedAuditEvent event = new LifecycleDeniedAuditEvent(
                requestId,
                subjectId,
                reasonCode,
                httpMethod,
                path,
                ip,
                userAgent
        );

        repository.save(event);
    }
}
