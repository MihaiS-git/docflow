package com.brutecx.docflow_backend.security.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AbsoluteSessionTimeoutFilterTest {

    @Test
    void invalidatesSessionWhenAbsoluteTimeoutExceeded() throws Exception {
        SessionSecurityProperties props = new SessionSecurityProperties(Duration.ofMillis(1), 1);
        AbsoluteSessionTimeoutFilter filter = new AbsoluteSessionTimeoutFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        HttpSession session = req.getSession(true);

        // Ensure creation time is in the past relative to "now".
        Thread.sleep(2);

        filter.doFilter(req, res, chain);

        // Session should be invalidated; subsequent access should create new session.
        // (MockHttpServletRequest doesn't expose invalidation directly, so we verify chain proceeds.)
        verify(chain, never()).doFilter(any(), any());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getStatus());

    }
}
