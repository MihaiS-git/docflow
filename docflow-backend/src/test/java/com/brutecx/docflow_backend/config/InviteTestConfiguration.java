package com.brutecx.docflow_backend.config;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

@TestConfiguration
public class InviteTestConfiguration {

    @Bean
    public InviteApplicationService inviteApplicationService() {
        return new InviteApplicationService(null, null, null, null, null, null, null, null) {
            @Override
            public void createAndSendInvite(
                    UUID tenantId,
                    String email,
                    String firstName,
                    String lastName,
                    String jobTitle,
                    String department
            ) {
                // no-op
            }
        };
    }
}
