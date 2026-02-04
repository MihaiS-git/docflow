package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapActivationService {

    private final UserRepository userRepository;
    private final TenantService tenantService;

    @Transactional
    public void activateBootstrapAdmin(String subject) {
        Tenant tenant = tenantService.getCurrentTenant();

        if (!tenant.isBootstrapEnabled()) {
            throw new IllegalStateException("Bootstrap already completed");
        }

        User admin = userRepository
                .findByTenantIdAndStatus(tenant.getId(), UserStatus.LOCKED)
                .orElseThrow(() ->
                        new IllegalStateException("No LOCKED bootstrap admin found"));

        if (admin.getExternalSubjectId() != null) {
            throw new IllegalStateException("Bootstrap admin already bound");
        }

        admin.bindExternalSubjectId(subject);
        admin.activate();

        tenant.disableBootstrap(); // bootstrap_enabled = false

        userRepository.save(admin);
    }
}
