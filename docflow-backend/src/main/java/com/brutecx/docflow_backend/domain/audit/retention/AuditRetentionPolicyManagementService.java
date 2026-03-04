package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.RetentionPolicyAuditMetadata;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuditRetentionPolicyManagementService {

    private final AuditRetentionPolicyRepository repository;
    private final IAdminAuditEventService adminAuditService;
    private final UserService userService;
    private final TenantService tenantService;

    public AuditRetentionPolicyManagementService(
            AuditRetentionPolicyRepository repository,
            IAdminAuditEventService adminAuditService,
            UserService userService,
            TenantService tenantService
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.adminAuditService = Objects.requireNonNull(adminAuditService);
        this.userService = Objects.requireNonNull(userService);
        this.tenantService = Objects.requireNonNull(tenantService);
    }

    @Transactional(readOnly = true)
    public List<AuditRetentionPolicy> listAll() {
        return repository.findAll();
    }

    @Transactional
    public AuditRetentionPolicy upsert(
            String streamName,
            int retentionDays,
            boolean archiveEnabled
    ) {
        String normalized = null;

        try {
            normalized = normalize(streamName);

            if (!AuditRetentionStreamRegistry.STREAM_NAMES.contains(normalized)) {
                throw new IllegalArgumentException("Unknown streamName");
            }

            if (retentionDays <= 0) {
                throw new IllegalArgumentException("retentionDays must be >= 1");
            }

            AuditRetentionPolicy existing =
                    repository.findByStreamName(normalized).orElse(null);

            Integer oldDays = existing != null ? existing.getRetentionDays() : null;
            Boolean oldArchive = existing != null ? existing.isArchiveEnabled() : null;

            boolean changed;

            AuditRetentionPolicy policy;

            if (existing == null) {
                policy = new AuditRetentionPolicy(
                        normalized,
                        retentionDays,
                        archiveEnabled
                );
                changed = true;
            } else {

                changed =
                        existing.getRetentionDays() != retentionDays
                                || existing.isArchiveEnabled() != archiveEnabled;

                if (changed) {
                    existing.update(retentionDays, archiveEnabled);
                }

                policy = existing;
            }

            AuditRetentionPolicy saved = repository.save(policy);

            if (changed) {

                User actor = userService.getRequiredCurrentUser();
                UUID tenantId = tenantService.getRootTenant().getId();

                RetentionPolicyAuditMetadata metadata =
                        new RetentionPolicyAuditMetadata(
                                normalized,
                                oldDays,
                                retentionDays,
                                oldArchive,
                                archiveEnabled
                        );

                adminAuditService.record(
                        AdminAuditActionType.RETENTION_POLICY_UPSERT,
                        tenantId,
                        "AUDIT_RETENTION_POLICY",
                        actor.getId(),
                        metadata
                );
            }

            return saved;
        } catch (RuntimeException ex) {
            recordFailure(normalized, retentionDays, archiveEnabled);
            throw ex;
        }
    }

    private void recordFailure(
            String normalized,
            int retentionDays,
            boolean archiveEnabled
    ) {
        try {
            User actor = userService.getRequiredCurrentUser();
            UUID tenantId = tenantService.getRootTenant().getId();

            RetentionPolicyAuditMetadata metadata =
                    new RetentionPolicyAuditMetadata(
                            normalized,
                            null,
                            retentionDays,
                            null,
                            archiveEnabled
                    );

            adminAuditService.record(
                    AdminAuditActionType.RETENTION_POLICY_UPSERT_FAILED,
                    tenantId,
                    "AUDIT_RETENTION_POLICY",
                    actor.getId(),
                    metadata
            );
        } catch (Exception ignored) {
            // Do not override original exception
        }
    }

    private String normalize(String stream) {
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("streamName must not be blank");
        }
        return stream.trim().toUpperCase(Locale.ROOT);
    }
}