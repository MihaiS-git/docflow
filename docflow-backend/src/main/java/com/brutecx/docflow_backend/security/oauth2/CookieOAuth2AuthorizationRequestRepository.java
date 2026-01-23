package com.brutecx.docflow_backend.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;
import org.springframework.web.util.WebUtils;

import java.util.Base64;

public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_AUTHZ_REQ";
    private static final int MAX_AGE_SECONDS = 180; // 3 minutes
    private static final String COOKIE_PATH = "/";

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
        Object obj = SerializationUtils.deserialize(decoded);
        return (obj instanceof OAuth2AuthorizationRequest req) ? req : null;
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(response);
            return;
        }

        byte[] bytes = SerializationUtils.serialize(authorizationRequest);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(MAX_AGE_SECONDS);

        // Local dev over http → do not set Secure=true, otherwise cookie won’t be stored.
        // In HTTPS environments, consider setting Secure=true at the proxy layer.
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(response);
        return existing;
    }

    private void removeAuthorizationRequestCookies(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
