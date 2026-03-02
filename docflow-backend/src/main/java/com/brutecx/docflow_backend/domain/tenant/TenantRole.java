package com.brutecx.docflow_backend.domain.tenant;

import java.util.HashSet;
import java.util.Set;

/**
 * Tenant-scoped roles (contextual RBAC).
 * Defined in code only. No Keycloak mapping.
 *
 * SECURITY: Explicit privilege levels — NEVER rely on enum ordinal.
 */
public enum TenantRole {
    MEMBER(1),
    EXECUTOR(2),
    REVIEWER(3),
    MANAGER(4);

    private final int level;

    TenantRole(int level) {
        this.level = level;
    }

    static {
        Set<Integer> seen = new HashSet<>();
        for (TenantRole r : values()) {
            if (r.level <= 0) {
                throw new IllegalStateException("Invalid role level for " + r + ": " + r.level);
            }
            if (!seen.add(r.level)) {
                throw new IllegalStateException("Duplicate role level detected: " + r);
            }
        }
    }

    public boolean isAtLeast(TenantRole required) {
        if (required == null) {
            throw new IllegalArgumentException("required role is null");
        }
        return this.level >= required.level;
    }

    public boolean isMorePrivilegedThan(TenantRole other) {
        if (other == null) {
            throw new IllegalArgumentException("other role is null");
        }
        return this.level > other.level;
    }

    public int level() {
        return level;
    }
}