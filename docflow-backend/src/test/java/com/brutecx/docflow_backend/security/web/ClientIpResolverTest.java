package com.brutecx.docflow_backend.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // ADDED: token supports setDetails
import org.springframework.security.web.authentication.WebAuthenticationDetails; // ADDED

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void prefersXForwardedForThenXRealIpThenRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.10");
        req.addHeader("X-Real-IP", "203.0.113.9");
        req.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");

        assertEquals("198.51.100.7", resolver.resolve(req)); // XFF first IP wins

        req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.10");
        req.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", resolver.resolve(req)); // X-Real-IP wins when XFF absent

        req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.10");
        assertEquals("172.18.0.10", resolver.resolve(req)); // remoteAddr fallback
    }

    @Test
    void prefersAuthenticationDetailsRemoteAddressOverHeaders() {
        ClientIpResolver resolver = new ClientIpResolver();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.10");
        req.addHeader("X-Forwarded-For", "198.51.100.7");
        req.addHeader("X-Real-IP", "203.0.113.9");

        // CHANGED: use concrete token type so we can call setDetails(...)
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken("user", "pw");

        // ADDED: inject WebAuthenticationDetails with remote address
        token.setDetails(new WebAuthenticationDetails(req));

        // CHANGED: auth details should win (even if headers exist)
        assertEquals("172.18.0.10", resolver.resolve(token, req));
    }
}
