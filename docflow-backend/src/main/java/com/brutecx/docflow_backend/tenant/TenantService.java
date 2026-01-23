package com.brutecx.docflow_backend.tenant;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
