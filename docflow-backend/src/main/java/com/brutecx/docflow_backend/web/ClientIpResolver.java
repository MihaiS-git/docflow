package com.brutecx.docflow_backend.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves client IP address in a reverse-proxy environment.
 * Precedence:
 * 1) Authentication details (when available)
 * 2) X-Forwarded-For (first IP)
 * 3) X-Real-IP
 * 4) request.getRemoteAddr()
 * All resolved IPs are validated (IPv4 or IPv6).
 * Invalid values are ignored.
 */
@Component
public class ClientIpResolver {

    public String resolve(Authentication authentication, HttpServletRequest request) {
        String fromDetails = resolveFromAuthenticationDetails(authentication);
        if (fromDetails != null) {
            return fromDetails;
        }
        return resolve(request);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }

        String ip;

        ip = firstIpFromXForwardedFor(request.getHeader("X-Forwarded-For"));
        if (ip != null) {
            return ip;
        }

        ip = validateIp(request.getHeader("X-Real-IP"));
        if (ip != null) {
            return ip;
        }

        ip = validateIp(request.getRemoteAddr());
        return ip != null ? ip : "UNKNOWN";
    }

    private static String resolveFromAuthenticationDetails(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();

        if (details instanceof WebAuthenticationDetails webDetails) {
            return validateIp(webDetails.getRemoteAddress());
        }

        if (details instanceof String s) {
            return validateIp(s);
        }

        return null;
    }

    private static String firstIpFromXForwardedFor(String xff) {
        if (xff == null || xff.isBlank()) {
            return null;
        }

        String value = xff.split(",")[0].trim();
        return validateIp(value);
    }

    /**
     * Validates IPv4 or IPv6.
     * Strips port if present.
     */
    private static String validateIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String candidate = raw.trim();

        // Strip port if present (IPv4:port)
        int colon = candidate.indexOf(':');
        if (colon > 0 && candidate.chars().filter(ch -> ch == ':').count() == 1) {
            candidate = candidate.substring(0, colon);
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}