package com.brutecx.docflow_backend.domain.tenant;

/**
* Tenant-scoped roles (contextual RBAC).
* Defined in code only. No Keycloak mapping.
*/
public enum TenantRole {
    MEMBER,
    EXECUTOR,
    REVIEWER,
    MANAGER;

    public boolean isAtLeast(TenantRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
