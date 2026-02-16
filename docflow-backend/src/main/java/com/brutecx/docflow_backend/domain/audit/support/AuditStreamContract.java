package com.brutecx.docflow_backend.domain.audit.support;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;

public final class AuditStreamContract {

    private AuditStreamContract() {
    }

    public static final int MAX_PAGE_SIZE = 100;

    public static final int VERIFY_BATCH_SIZE = 1_000;
    public static final int EXPORT_BATCH_SIZE = 2_000;

    public static final int EXPORT_MAX_ROWS = 200_000;

    public static void requireRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
    }

    public static void requireCursorPair(Instant cursorTimestamp, java.util.UUID cursorId) {
        boolean hasTs = cursorTimestamp != null;
        boolean hasId = cursorId != null;
        if (hasTs ^ hasId) {
            throw new IllegalArgumentException("cursorTimestamp and cursorId must be provided together");
        }
    }

    public static int safePageSize(int requested) {
        return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
    }

    public static void requireResponse(HttpServletResponse response) {
        if (response == null) throw new IllegalArgumentException("response is required");
    }
}
