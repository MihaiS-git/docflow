package com.brutecx.docflow_backend.domain.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Order(0)
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

        Tenant tenant = new Tenant("Brutecx");

        tenantRepository.save(tenant);
    }
}
