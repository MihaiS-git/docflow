package com.brutecx.docflow_backend.audit.identity;

public interface IUserIdentityProjectionService {
    void ensureProjected(String subjectId);
}
