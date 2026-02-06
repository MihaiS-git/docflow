package com.brutecx.docflow_backend.audit.rbac;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditServiceImpl
        implements IRbacDeniedAuditService {

    private final RbacDeniedAuditEventRepository repository;

    @Override
    @Transactional
    public void record(
            String requestId,
            String subjectId,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    ) {

        String fingerprint = EventFingerprint.of(List.of(
                "RBAC_DENIED",
                requestId,
                subjectId,
                httpMethod,
                path
        ));

        repository.save(
                new RbacDeniedAuditEvent(
                        requestId,
                        subjectId,
                        httpMethod,
                        path,
                        ip,
                        userAgent,
                        fingerprint
                )
        );
    }
}
