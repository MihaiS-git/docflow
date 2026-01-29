package com.brutecx.docflow_backend.security.audit.keycloak;

import com.brutecx.docflow_backend.security.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.security.audit.auth.AuthenticationEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class KeycloakAuthEventPullJobTest {
    KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    KeycloakEventCheckpointRepository checkpointRepo = mock(KeycloakEventCheckpointRepository.class);
    AuthenticationEventRepository authEventRepo = mock(AuthenticationEventRepository.class);

    KeycloakAuthEventPullJob job = new KeycloakAuthEventPullJob(
            keycloak, checkpointRepo, authEventRepo
    );

    @Test
    void persists_only_login_error_events_and_updates_checkpoint() {
        when(checkpointRepo.findById("KEYCLOAK_ADMIN_EVENTS")).thenReturn(Optional.empty());

        var loginError = new KeycloakAdminClient.KeycloakAdminEvent(
                1000L, "LOGIN_ERROR", "r", "c", "user-id-1", "sess-1", "10.0.0.2", "invalid_user_credentials",
                Map.of("user_agent", "UA-1")
        );
        var loginOk = new KeycloakAdminClient.KeycloakAdminEvent(
                2000L, "LOGIN", "r", "c", "user-id-1", "sess-1", "10.0.0.2", null,
                Map.of("user_agent", "UA-1")
        );

        when(keycloak.fetchEvents(0L)).thenReturn(List.of(loginError, loginOk));

        job.pull();

        ArgumentCaptor<AuthenticationEvent> captor = ArgumentCaptor.forClass(AuthenticationEvent.class);
        verify(authEventRepo, times(1)).save(captor.capture());
        AuthenticationEvent saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("user-id-1");
        assertThat(saved.getIp()).isEqualTo("10.0.0.2");
        assertThat(saved.getUserAgent()).isEqualTo("UA-1");
        assertThat(saved.getRequestId()).isEqualTo("sess-1");
        assertThat(saved.getTimestamp().toEpochMilli()).isEqualTo(1000L);

        ArgumentCaptor<KeycloakEventCheckpoint> cpCaptor = ArgumentCaptor.forClass(KeycloakEventCheckpoint.class);
        verify(checkpointRepo).save(cpCaptor.capture());
        assertThat(cpCaptor.getValue().getLastEventTimeMs()).isEqualTo(1001L);
        assertThat(saved.getEventFingerprint()).isNotBlank();
    }
}
