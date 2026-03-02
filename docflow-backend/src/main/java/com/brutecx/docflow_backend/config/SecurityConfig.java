package com.brutecx.docflow_backend.config;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakOidcUserService;
import com.brutecx.docflow_backend.security.enforcement.AuthenticatedAuthorizationManager;
import com.brutecx.docflow_backend.security.enforcement.LifecycleAuthorizationManager;
import com.brutecx.docflow_backend.security.enforcement.TenantAuthorizationManagerFactory;
import com.brutecx.docflow_backend.security.handler.ApiAuthenticationEntryPoint;
import com.brutecx.docflow_backend.security.handler.RestAccessDeniedHandler;
import com.brutecx.docflow_backend.security.session.filter.AbsoluteSessionTimeoutFilter;
import com.brutecx.docflow_backend.security.session.SessionSecurityProperties;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@Profile({"dev", "prod"})
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${docflow.security.keycloak-logout-uri}")
    private String keycloakLogoutUri;

    @Value("${docflow.security.post-logout-redirect-uri}")
    private String postLogoutRedirectUri;

    @Value("${docflow.security.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final SessionSecurityProperties sessionSecurityProperties;

    public SecurityConfig(SessionSecurityProperties sessionSecurityProperties) {
        this.sessionSecurityProperties = sessionSecurityProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver,
            RestAccessDeniedHandler restAccessDeniedHandler,
            LifecycleAuthorizationManager lifecycleAuthorizationManager,
            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
            RequestCorrelationIdFilter requestCorrelationIdFilter,
            KeycloakOidcUserService keycloakOidcUserService,
            InviteApplicationService inviteApplicationService,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            AuthenticatedAuthorizationManager authenticatedAuthorizationManager,
            TenantAuthorizationManagerFactory tenantAuthorizationManagerFactory
    ) throws Exception {

        http
                .addFilterBefore(requestCorrelationIdFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(absoluteSessionTimeoutFilter, SecurityContextHolderFilter.class)

                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                "/api/auth/logout",
                                "/api/csrf",
                                "/api/invites/validate"
                        )
                )

                .cors(Customizer.withDefaults())

                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(sessionSecurityProperties.maxConcurrentSessions())
                        .maxSessionsPreventsLogin(true)
                        .sessionRegistry(sessionRegistry())
                )

                .headers(headers -> {
                    headers
                            .contentTypeOptions(Customizer.withDefaults())
                            .xssProtection(Customizer.withDefaults())
                            .frameOptions(frame -> frame.sameOrigin())
                            .contentSecurityPolicy(csp ->
                                    csp.policyDirectives("default-src 'self'; frame-ancestors 'self'")
                            );

                    if ("prod".equals(activeProfile)) {
                        headers.httpStrictTransportSecurity(hsts ->
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000)
                        );
                    }
                })

                .authorizeHttpRequests(auth -> auth

                        // -------- PUBLIC --------
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/login/**",
                                "/oauth2/**"
                        ).permitAll()

                        // -------- AUTH --------
                        .requestMatchers(HttpMethod.POST, "/api/invites/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()

                        // -------- SAFE IDENTITY --------
                        .requestMatchers(HttpMethod.GET, "/api/auth/me")
                        .access(authenticatedAuthorizationManager)

                        .requestMatchers(HttpMethod.GET, "/api/users/me")
                        .access(authenticatedAuthorizationManager)

                        // -------- BOOTSTRAP --------
                        .requestMatchers("/api/bootstrap/**")
                        .access(AuthorityAuthorizationManager.hasRole("ADMIN"))

                        // -------- ADMIN --------
                        .requestMatchers("/api/admin/**")
                        .access(AuthorizationManagers.allOf(
                                lifecycleAuthorizationManager,
                                AuthorityAuthorizationManager.hasRole("ADMIN")
                        ))

                        // -------- AUDIT --------
                        .requestMatchers("/api/audit/**")
                        .access(AuthorizationManagers.allOf(
                                lifecycleAuthorizationManager,
                                AuthorityAuthorizationManager.hasAnyRole("AUDITOR")
                        ))

                        // -------- AUDIT KEY DISTRIBUTION (AUTHENTICATED) --------
                        .requestMatchers("/api/security/audit-keys/**")
                        .access(AuthorizationManagers.allOf(
                                lifecycleAuthorizationManager,
                                AuthorityAuthorizationManager.hasAnyRole("AUDITOR", "ADMIN")
                        ))

                        // -------- TENANT INVITES (MANAGER) --------
                        .requestMatchers("/api/tenants/*/invites/**")
                        .access(AuthorizationManagers.allOf(
                                authenticatedAuthorizationManager,
                                lifecycleAuthorizationManager,
                                tenantAuthorizationManagerFactory.atLeast(TenantRole.MANAGER)
                        ))

                        // -------- TENANT DATA (MANDATORY SCOPE) --------
                        .requestMatchers("/api/tenants/**")
                        .access(AuthorizationManagers.allOf(
                                authenticatedAuthorizationManager,
                                lifecycleAuthorizationManager,
                                tenantAuthorizationManagerFactory.atLeast(TenantRole.MEMBER)
                        ))

                        // -------- HARD DENY FALLBACK --------
                        .requestMatchers("/api/**").denyAll()

                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(restAccessDeniedHandler)
                        .authenticationEntryPoint((req, res, authEx) -> {
                            if (req.getRequestURI().startsWith("/api/")) {
                                apiAuthenticationEntryPoint.commence(req, res, authEx);
                                return;
                            }
                            new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak")
                                    .commence(req, res, authEx);
                        })
                )

                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authz ->
                                authz.authorizationRequestResolver(pkceAuthorizationRequestResolver)
                        )
                        .redirectionEndpoint(redirection ->
                                redirection.baseUri("/login/oauth2/code/*")
                        )
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(keycloakOidcUserService)
                        )
                        .successHandler((req, res, auth) -> {
                            if (auth.getPrincipal() instanceof OidcUser) {
                                inviteApplicationService.consumeInviteIfPresent(
                                        req.getSession(false)
                                );
                            }
                            res.sendRedirect(frontendBaseUrl);
                        })
                        .failureHandler((req, res, ex) -> {
                            res.sendRedirect(frontendBaseUrl);
                        })
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            if (auth instanceof OAuth2AuthenticationToken oauth &&
                                    oauth.getPrincipal() instanceof OidcUser oidcUser) {

                                String redirect =
                                        keycloakLogoutUri +
                                                "?id_token_hint=" +
                                                URLEncoder.encode(
                                                        oidcUser.getIdToken().getTokenValue(),
                                                        StandardCharsets.UTF_8
                                                ) +
                                                "&post_logout_redirect_uri=" +
                                                URLEncoder.encode(
                                                        postLogoutRedirectUri,
                                                        StandardCharsets.UTF_8
                                                );

                                res.sendRedirect(redirect);
                                return;
                            }
                            res.sendRedirect(postLogoutRedirectUri);
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        "/oauth2/authorization"
                );

        resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce()
        );

        return resolver;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${docflow.security.allowed-origins:}") String allowedOriginsCsv
    ) {
        CorsConfiguration config = new CorsConfiguration();

        if (allowedOriginsCsv != null && !allowedOriginsCsv.isBlank()) {
            List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    ServletContextInitializer sessionCookieInitializer() {
        return servletContext -> {
            var cookie = servletContext.getSessionCookieConfig();
            cookie.setHttpOnly(true);
            cookie.setSecure("prod".equals(activeProfile));
            cookie.setName("JSESSIONID");
            cookie.setPath("/");
        };
    }
}
