package com.brutecx.docflow_backend.audit;

import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventListener;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventRepository;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.audit.identity.IUserIdentityProjectionService;
import com.brutecx.docflow_backend.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class AuthenticationEventListenerTest {

    AuthenticationEventRepository repo = mock(AuthenticationEventRepository.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    IUserIdentityProjectionService identityProjectionService =
            mock(IUserIdentityProjectionService.class);
    ClientIpResolver clientIpResolver = new ClientIpResolver();


    AuthenticationEventListener listener =
            new AuthenticationEventListener(repo, request, identityProjectionService, clientIpResolver);

    @BeforeEach
    void setUp() {
        MDC.put("requestId", "test-request-id");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void login_success_is_persisted() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        listener.onSuccess(
                new AuthenticationSuccessEvent(
                        new TestingAuthenticationToken("user@test", "pwd")
                )
        );

        verifySaved(AuthenticationResult.SUCCESS);

        verify(identityProjectionService)
                .ensureProjected("UNKNOWN");
    }

    @Test
    void login_failure_is_persisted() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        AbstractAuthenticationFailureEvent event =
                new AuthenticationFailureBadCredentialsEvent(
                        new TestingAuthenticationToken("user@test", "pwd"),
                        new AuthenticationException("bad credentials") {
                        }
                );

        listener.onFailure(event);

        verifySaved(AuthenticationResult.FAILURE);
    }

    @Test
    void logout_is_persisted() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        listener.onLogout(
                new LogoutSuccessEvent(
                        new TestingAuthenticationToken("user@test", "pwd")
                )
        );

        verifySaved(AuthenticationResult.LOGOUT);
    }

    private void verifySaved(AuthenticationResult expectedResult) {
        ArgumentCaptor<AuthenticationEvent> captor =
                ArgumentCaptor.forClass(AuthenticationEvent.class);

        verify(repo).save(captor.capture());

        AuthenticationEvent event = captor.getValue();

        assertThat(event.getResult()).isEqualTo(expectedResult);
        assertThat(event.getResult().getValue()).isEqualTo(expectedResult.getValue());
        assertThat(event.getUsername()).isEqualTo("user@test");
    }
}
