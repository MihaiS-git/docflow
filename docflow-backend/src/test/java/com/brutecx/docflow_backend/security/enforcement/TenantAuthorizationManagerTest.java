package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantAuthorizationManagerTest {

    @Test
    void denies_when_membership_missing_and_audits_with_tenant_enriched_reason() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantMembershipRepository membershipRepository = mock(UserTenantMembershipRepository.class);
        ILifecycleDeniedAuditService deniedAudit = mock(ILifecycleDeniedAuditService.class);

        TenantAuthorizationManager mgr =
                new TenantAuthorizationManager(TenantRole.MEMBER, userRepository, membershipRepository, deniedAudit);

        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/docs");

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("sub-1");

        var auth = new TestingAuthenticationToken(oidcUser, "n/a", "ROLE_USER");
        auth.setAuthenticated(true);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByExternalSubjectId("sub-1")).thenReturn(Optional.of(user));

        when(membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)).thenReturn(Optional.empty());

        AuthorizationDecision decision = mgr.check(() -> auth, new RequestAuthorizationContext(req));
        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();

        verify(deniedAudit).record(
                eq("sub-1"),
                eq("TENANT_MEMBERSHIP_MISSING:" + tenantId),
                eq("GET"),
                eq("/api/tenants/" + tenantId + "/docs"),
                any()
        );
    }

    @Test
    void denies_when_membership_inactive() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantMembershipRepository membershipRepository = mock(UserTenantMembershipRepository.class);
        ILifecycleDeniedAuditService deniedAudit = mock(ILifecycleDeniedAuditService.class);

        TenantAuthorizationManager mgr =
                new TenantAuthorizationManager(TenantRole.MEMBER, userRepository, membershipRepository, deniedAudit);

        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/docs");

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("sub-1");

        var auth = new TestingAuthenticationToken(oidcUser, "n/a", "ROLE_USER");
        auth.setAuthenticated(true);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByExternalSubjectId("sub-1")).thenReturn(Optional.of(user));

        UserTenantMembership membership = mock(UserTenantMembership.class);
        when(membership.getStatus()).thenReturn(MembershipStatus.SUSPENDED);
        when(membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)).thenReturn(Optional.of(membership));

        AuthorizationDecision decision = mgr.check(() -> auth, new RequestAuthorizationContext(req));
        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();

        verify(deniedAudit).record(
                eq("sub-1"),
                eq("TENANT_MEMBERSHIP_NOT_ACTIVE:" + tenantId),
                eq("GET"),
                eq("/api/tenants/" + tenantId + "/docs"),
                any()
        );
    }

    @Test
    void denies_when_role_insufficient() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantMembershipRepository membershipRepository = mock(UserTenantMembershipRepository.class);
        ILifecycleDeniedAuditService deniedAudit = mock(ILifecycleDeniedAuditService.class);

        TenantAuthorizationManager mgr =
                new TenantAuthorizationManager(TenantRole.MANAGER, userRepository, membershipRepository, deniedAudit);

        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/docs");

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("sub-1");

        var auth = new TestingAuthenticationToken(oidcUser, "n/a", "ROLE_USER");
        auth.setAuthenticated(true);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByExternalSubjectId("sub-1")).thenReturn(Optional.of(user));

        UserTenantMembership membership = mock(UserTenantMembership.class);
        when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(membership.getRole()).thenReturn(TenantRole.MEMBER);
        when(membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)).thenReturn(Optional.of(membership));

        AuthorizationDecision decision = mgr.check(() -> auth, new RequestAuthorizationContext(req));
        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();

        verify(deniedAudit).record(
                eq("sub-1"),
                eq("TENANT_ROLE_INSUFFICIENT:" + tenantId),
                eq("GET"),
                eq("/api/tenants/" + tenantId + "/docs"),
                any()
        );
    }

    @Test
    void allows_when_role_sufficient() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantMembershipRepository membershipRepository = mock(UserTenantMembershipRepository.class);
        ILifecycleDeniedAuditService deniedAudit = mock(ILifecycleDeniedAuditService.class);

        TenantAuthorizationManager mgr =
                new TenantAuthorizationManager(TenantRole.REVIEWER, userRepository, membershipRepository, deniedAudit);

        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/docs");

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("sub-1");

        var auth = new TestingAuthenticationToken(oidcUser, "n/a", "ROLE_USER");
        auth.setAuthenticated(true);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByExternalSubjectId("sub-1")).thenReturn(Optional.of(user));

        UserTenantMembership membership = mock(UserTenantMembership.class);
        when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(membership.getRole()).thenReturn(TenantRole.MANAGER);
        when(membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)).thenReturn(Optional.of(membership));

        AuthorizationDecision decision = mgr.check(() -> auth, new RequestAuthorizationContext(req));
        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();

        verifyNoInteractions(deniedAudit);
    }
}
