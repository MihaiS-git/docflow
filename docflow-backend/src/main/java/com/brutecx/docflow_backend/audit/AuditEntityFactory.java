package com.brutecx.docflow_backend.audit;

import com.brutecx.docflow_backend.audit.tamper.AuditChainService;

@FunctionalInterface
public interface AuditEntityFactory<T> {

    T create(AuditChainService.PreparedChainHash prepared);

}