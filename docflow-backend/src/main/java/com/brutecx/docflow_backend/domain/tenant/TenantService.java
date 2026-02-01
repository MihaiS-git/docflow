package com.brutecx.docflow_backend.domain.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;

    @Transactional
    public Tenant getCurrentTenant() {
        return tenantRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("No tenant found. Bootstrap tenant is required.")
                );
    }
}
