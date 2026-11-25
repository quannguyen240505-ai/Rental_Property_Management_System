package com.rental.rental.security;

import com.rental.rental.security.AuthIntrospectionClient.AuthUser;
import com.rental.rental.security.AuthIntrospectionClient.AuthServiceUnavailableException;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BearerTokenFilterTest {

    private AuthIntrospectionClient authClient;
    private BearerTokenFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        authClient = mock(AuthIntrospectionClient.class);
        filter = new BearerTokenFilter(authClient);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);

        request.setServletPath("/api/contracts");
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingToken_returns401() throws Exception {
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(authClient);
        verifyNoInteractions(chain);
    }

    @Test
    void invalidToken_returns401() throws Exception {
        request.addHeader("Authorization", "Bearer invalid");

        when(authClient.introspect("invalid"))
                .thenReturn(new AuthUser(
                        false, null, null, null, null
                ));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void revokedToken_returns401() throws Exception {
        request.addHeader("Authorization", "Bearer revoked");

        when(authClient.introspect("revoked"))
                .thenReturn(new AuthUser(
                        false, "user-1", "test", "OWNER", null
                ));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void authUnavailable_returns503() throws Exception {
        request.addHeader("Authorization", "Bearer token");

        when(authClient.introspect("token"))
                .thenThrow(new AuthServiceUnavailableException());

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void validOwnerToken_authenticates() throws Exception {
        request.addHeader("Authorization", "Bearer valid");

        when(authClient.introspect("valid"))
                .thenReturn(new AuthUser(
                        true, "user-1", "owner", "OWNER", null
                ));

        doAnswer(invocation -> {
            var authentication =
                    SecurityContextHolder.getContext()
                            .getAuthentication();

            assertNotNull(authentication);
            assertEquals("user-1",
                    authentication.getPrincipal());

            assertTrue(authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority()
                            .equals("ROLE_OWNER")));

            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);

        assertNull(SecurityContextHolder.getContext()
                .getAuthentication());
    }
}
