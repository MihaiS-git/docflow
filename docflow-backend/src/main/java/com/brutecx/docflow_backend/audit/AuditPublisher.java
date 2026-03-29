package com.brutecx.docflow_backend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Component
public class AuditPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AuditPublisher.class);

    public void publishAfterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
            return;
        }

        runSafely(action);
    }

    public void publishNow(Runnable action) {
        Objects.requireNonNull(action, "action");
        runSafely(action);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            LOG.error("audit_publish_failed", ex);
        }
    }
}