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

    @Test
    void ownerWithValidToken_canAccess() {
        UUID ownerId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer good.jwt.value");
        when(authClient.introspectBearerToken("Bearer good.jwt.value"))
                .thenReturn(new TokenIntrospectionResponse(
                        true, ownerId, "owner", "OWNER", Instant.now().plusSeconds(600)));
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("authenticatedUserId", ownerId);
    }

    @Test
    void staffWithoutAssignmentPolicy_gets403() {
        when(request.getHeader("Authorization")).thenReturn("Bearer staff.jwt.value");
        when(authClient.introspectBearerToken("Bearer staff.jwt.value"))
                .thenReturn(new TokenIntrospectionResponse(
                        true, UUID.randomUUID(), "staff", "STAFF", Instant.now().plusSeconds(600)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void invalidToken_gets401() {
        when(authClient.introspectBearerToken(null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void authUnavailable_gets503() {
        when(authClient.introspectBearerToken(null))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    void optionsPreflight_passesWithoutIntrospection() {
        when(request.getMethod()).thenReturn("OPTIONS");
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(authClient);
    }
}
