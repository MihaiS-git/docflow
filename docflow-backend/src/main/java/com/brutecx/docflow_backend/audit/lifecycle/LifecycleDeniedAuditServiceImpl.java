package com.brutecx.docflow_backend.audit.lifecycle;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        String fingerprint = EventFingerprint.of(List.of(
                requestId,
                subjectId,
                reasonCode,
                httpMethod,
                path,
                ip,
                userAgent
        ));

        LifecycleDeniedAuditEvent event = new LifecycleDeniedAuditEvent(
                requestId,
                subjectId,
                reasonCode,
                httpMethod,
                path,
                ip,
                userAgent,
                fingerprint
        );

        repository.save(event);
    }
}
