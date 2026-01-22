package com.brutecx.docflow_backend.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.brutecx.docflow_backend.security.enforcement.LifecycleAuthorizationManager;
import com.brutecx.docflow_backend.security.handler.RestAccessDeniedHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile({"dev", "prod"})
public class SecurityConfig {

    @Value("${docflow.security.keycloak-logout-uri}")
    private String keycloakLogoutUri;

    @Value("${docflow.security.post-logout-redirect-uri}")
    private String postLogoutRedirectUri;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver,
            RestAccessDeniedHandler restAccessDeniedHandler,
            LifecycleAuthorizationManager lifecycleAuthorizationManager
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/logout")
                )
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**")
                        .access(lifecycleAuthorizationManager)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/login/**",
                                "/oauth2/**"
                        ).permitAll()
                        // Example role-based protection points (adjust when real routes exist)
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasAnyRole("AUDITOR", "ADMIN")
                        .requestMatchers("/api/review/**").hasAnyRole("REVIEWER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(restAccessDeniedHandler)
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak"),
                                request -> !request.getRequestURI().startsWith("/api/")
                                        && !request.getRequestURI().startsWith("/actuator")
                        )
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(pkceAuthorizationRequestResolver)
                        )
                        .userInfoEndpoint(userInfo -> {
                        })
                        .defaultSuccessUrl("http://localhost:3000", true)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            if (auth instanceof OAuth2AuthenticationToken oauth &&
                                    oauth.getPrincipal() instanceof OidcUser oidcUser) {
                                String idToken = oidcUser.getIdToken().getTokenValue();
                                String redirect =
                                        keycloakLogoutUri +
                                                "?id_token_hint=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8) +
                                                "&post_logout_redirect_uri=" +
                                                URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8);
                                res.sendRedirect(redirect);
                                return;
                            }

                            // If auth is null / cleared (e.g. blocked user flow),
                            // do not call Keycloak logout endpoint (it may 400 without id_token_hint).
                            res.sendRedirect(postLogoutRedirectUri);
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return (authorities) -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidcAuth) {
                    // Check both Attributes and ID Token claims
                    Map<String, Object> realmAccess = oidcAuth.getAttributes().containsKey("realm_access")
                            ? (Map<String, Object>) oidcAuth.getAttributes().get("realm_access")
                            : oidcAuth.getIdToken().getClaim("realm_access");

                    if (realmAccess != null && realmAccess.containsKey("roles")) {
                        List<String> roles = (List<String>) realmAccess.get("roles");
                        roles.forEach(role -> mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                    }
                }
                mappedAuthorities.add(authority);
            });

            return mappedAuthorities;
        };
    }

    /**
     * Forces PKCE (S256) for the /oauth2/authorization/{registrationId} endpoint.
     * This fixes Keycloak error: "Missing parameter: code_challenge_method".
     */
    @Bean
    OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        "/oauth2/authorization"
                );

        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
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

//    private String extractIdToken(Authentication authentication) {
//        if (authentication == null) {
//            return "";
//        }
//
//        Object principal = authentication.getPrincipal();
//        if (principal instanceof OidcUser oidcUser) {
//            return oidcUser.getIdToken().getTokenValue();
//        }
//
//        return "";
//    }
}
