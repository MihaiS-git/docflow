package com.brutecx.docflow_backend.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication; // ADDED: support auth details extraction
import org.springframework.security.web.authentication.WebAuthenticationDetails; // ADDED: common Spring Security details type
import org.springframework.stereotype.Component;

/**
 * Single source of truth for resolving the client IP address in a trusted reverse-proxy setup.
 * Precedence:
 * 1) Authentication details (when available)
 * 2) X-Forwarded-For (first IP)
 * 3) X-Real-IP
 * 4) request.getRemoteAddr()
 */
@Component
public class ClientIpResolver {

    // ADDED: auth-aware resolver for Spring Security event listeners
    public String resolve(Authentication authentication, HttpServletRequest request) {
        // ADDED: first try Spring Security details (most reliable inside auth events)
        String ipFromDetails = resolveFromAuthenticationDetails(authentication);
        if (ipFromDetails != null) {
            return ipFromDetails;
        }

        // ADDED: fall back to request headers / remoteAddr
        return resolve(request);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }

        String xff = request.getHeader("X-Forwarded-For");
        String ip = firstIpFromXForwardedFor(xff);
        if (ip != null) {
            return ip;
        }

        String xRealIp = trimToNull(request.getHeader("X-Real-IP"));
        if (xRealIp != null) {
            return xRealIp;
        }

        String remoteAddr = trimToNull(request.getRemoteAddr());
        return remoteAddr != null ? remoteAddr : "UNKNOWN";
    }

    // ADDED: resolves client IP from Spring Security authentication details
    private static String resolveFromAuthenticationDetails(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails webDetails) {
            return trimToNull(webDetails.getRemoteAddress());
        }

        // ADDED: safe fallback for any custom details types (string-form)
        if (details instanceof String s) {
            return trimToNull(s);
        }

        return null;
    }

    private static String firstIpFromXForwardedFor(String xff) {
        String value = trimToNull(xff);
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma);
        }
        return trimToNull(value);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
