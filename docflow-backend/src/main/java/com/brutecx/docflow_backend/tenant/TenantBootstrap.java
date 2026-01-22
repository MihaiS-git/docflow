package com.brutecx.docflow_backend.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Profile({"dev", "prod"})
public class TenantBootstrap implements ApplicationRunner {

    private final TenantRepository tenantRepository;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        Optional<Tenant> existing = tenantRepository.findAll()
                .stream()
                .findFirst();

        if(existing.isPresent()){
            return;
        }

        Tenant tenant = Tenant.builder()
                .name("Brutecx")
                .status(TenantStatus.ACTIVE)
                .build();

        tenantRepository.save(tenant);
    }
}
