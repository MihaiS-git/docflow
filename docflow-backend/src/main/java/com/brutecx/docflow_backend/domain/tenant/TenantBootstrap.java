package com.brutecx.docflow_backend.domain.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Order(0)
@Profile({"dev", "prod"})
public class TenantBootstrap implements ApplicationRunner {

    private final TenantRepository tenantRepository;


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            return;
        }

        Tenant tenant = Tenant.bootstrapTenant("Brutecx");
        tenantRepository.save(tenant);
    }
}
