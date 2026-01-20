package com.brutecx.docflow_backend.security.audit.identity;

public interface IUserIdentityProjectionService {
    void ensureProjected(String subjectId);
}
