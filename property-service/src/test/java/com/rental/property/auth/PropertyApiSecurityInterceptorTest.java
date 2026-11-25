package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropertyApiSecurityInterceptorTest {
    private AuthIntrospectionClient authClient;
    private PropertyApiSecurityInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        authClient = mock(AuthIntrospectionClient.class);
        interceptor = new PropertyApiSecurityInterceptor(authClient);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
    }

    private void mockIdentity(String token, String role, UUID id) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(authClient.introspectBearerToken("Bearer " + token))
                .thenReturn(new TokenIntrospectionResponse(true, id,
                        role.toLowerCase(), role, Instant.now().plusSeconds(600)));
    }

    @Test void ownerWithValidToken_canAccess() {
        UUID id = UUID.randomUUID();
        mockIdentity("owner.jwt.value", "OWNER", id);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("authenticatedUserId", id);
        verify(request).setAttribute("authenticatedRole", "OWNER");
    }

    @Test void staffWithValidToken_canProceedToPropertyScopeCheck() {
        UUID id = UUID.randomUUID();
        mockIdentity("staff.jwt.value", "STAFF", id);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("authenticatedUserId", id);
        verify(request).setAttribute("authenticatedRole", "STAFF");
    }

    @Test void customerIsForbidden() {
        mockIdentity("customer.jwt.value", "CUSTOMER", UUID.randomUUID());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test void invalidToken_gets401() {
        when(authClient.introspectBearerToken(null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test void authUnavailable_gets503() {
        when(authClient.introspectBearerToken(null))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test void optionsPreflight_passesWithoutIntrospection() {
        when(request.getMethod()).thenReturn("OPTIONS");
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(authClient);
    }
}
