package com.brutecx.docflow_backend.audit.sensitive;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditServiceImpl
        implements ISensitiveAccessAuditService {

    private final SensitiveAccessAuditEventRepository repository;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    @Transactional
    public void record(
            UUID actorUserId,
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String correlationId,
            String ip,
            String userAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
    ) {

        try {
            repository.save(new SensitiveAccessAuditEvent(
                    actorUserId,
                    actorExternalSubjectId,
                    tenantId,
                    subjectType,
                    subjectId,
                    resource,
                    action,
                    resourcePath,
                    correlationId,
                    ip,
                    userAgent,
                    reasonCode,
                    reasonDetail,
                    dataClassification,
                    eventFingerprint
            ));

        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "SENSITIVE ACCESS AUDIT DEDUPLICATED. correlationId={} fingerprint={}",
                    correlationId,
                    eventFingerprint
            );
        } catch (Exception ex) {
            log.error(
                    "SENSITIVE ACCESS AUDIT FAILURE. correlationId={} subjectType={} subjectId={} resource={} action={}",
                    correlationId,
                    subjectType,
                    subjectId,
                    resource,
                    action,
                    ex
            );
        }
    }
}
