package com.brutecx.docflow_backend.audit;

import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.ChainSegmentAware;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Objects;

@Service
public class AuditStreamExecutor {

    public enum WriteOutcome {
        SUCCESS,
        DEDUP
    }

    private final AuditChainService auditChainService;
    private final AuditWriteFailureMetrics metrics;
    private final TransactionTemplate txTemplate;

    public AuditStreamExecutor(
            AuditChainService auditChainService,
            AuditWriteFailureMetrics metrics,
            PlatformTransactionManager txManager
    ) {
        this.auditChainService = Objects.requireNonNull(auditChainService);
        this.metrics = Objects.requireNonNull(metrics);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(txManager));
    }

    public <T> WriteOutcome execute(
            String stream,
            String execCtx,
            AuditPartition partition,
            String canonicalMaterial,
            JpaRepository<T, ?> repository,
            AuditEntityFactory<T> factory
    ) {
        final long startNs = System.nanoTime();
        try {
            WriteOutcome outcome = txTemplate.execute(status -> {
                try {
                    AuditChainService.PreparedChainHash prepared =
                            auditChainService.prepareHash(partition, canonicalMaterial);

                    T entity = factory.create(prepared);

                    if (prepared.segment() && entity instanceof ChainSegmentAware chainSegmentAware) {
                        chainSegmentAware.setChainSegmentHash(prepared.eventHash());
                    }

                    repository.saveAndFlush(entity);

                    auditChainService.commitHash(partition, prepared);

                    return WriteOutcome.SUCCESS;
                } catch (DataIntegrityViolationException ignored) {
                    status.setRollbackOnly();
                    return WriteOutcome.DEDUP;

                } catch (RuntimeException ex) {
                    status.setRollbackOnly();
                    throw ex;
                }
            });

            WriteOutcome finalOutcome = outcome != null ? outcome : WriteOutcome.SUCCESS;

            if (finalOutcome == WriteOutcome.SUCCESS) {
                metrics.incrementSuccess(stream, execCtx);
            } else {
                metrics.incrementDedup(stream, execCtx);
            }

            return finalOutcome;
        } catch (Exception ex) {
            metrics.incrementFailure(stream, execCtx, ex);
            throw ex;
        } finally {
            metrics.recordLatency(
                    stream,
                    execCtx,
                    Duration.ofNanos(System.nanoTime() - startNs)
            );
        }
    }
}