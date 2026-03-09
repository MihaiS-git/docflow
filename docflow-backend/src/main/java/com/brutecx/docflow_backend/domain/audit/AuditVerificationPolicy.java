package com.brutecx.docflow_backend.domain.audit;

public final class AuditVerificationPolicy {

    private AuditVerificationPolicy() {}

    /**
     * Number of events loaded per DB page during verification.
     */
    public static final int VERIFY_BATCH_SIZE = 5_000;

    /**
     * Hard cap for verification to prevent extremely large scans.
     */
    public static final long VERIFY_MAX_EVENTS = 200_000;

}