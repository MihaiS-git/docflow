package com.brutecx.docflow_backend.security.audit.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdminAuditEventRepositoryTest {

    @Autowired
    private AdminAuditEventRepository repository;

    @Test
    void persists_admin_audit_event() {
        UUID actor = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        AdminAuditEvent event = new AdminAuditEvent(
                actor,
                tenant,
                AdminAuditActionType.USER_DISABLED,
                target,
                new UserStateChangeMetadata(
                        UserStateChangeReason.MANUAL_ADMIN_ACTION,
                        "manual disable"
                )
        );

        AdminAuditEvent saved = repository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getActorUserId()).isEqualTo(actor);
        assertThat(saved.getTenantId()).isEqualTo(tenant);
        assertThat(saved.getTargetUserId()).isEqualTo(target);
        assertThat(saved.getMetadata()).isInstanceOf(UserStateChangeMetadata.class);
    }
}

