package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewingInterceptorTest {
    @Mock AuthIntrospectionClient auth;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    PropertyApiSecurityInterceptor interceptor;
    UUID customer = UUID.randomUUID();

    @BeforeEach
    void init(){ interceptor=new PropertyApiSecurityInterceptor(auth); }

    private void setup(String role, String path) {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer test");
        when(request.getRequestURI()).thenReturn(path);
        when(auth.introspectBearerToken("Bearer test")).thenReturn(
                new TokenIntrospectionResponse(true,customer,"qa",role,Instant.now().plusSeconds(300)));
    }

    @Test
    void customerMayUseViewingsApi() {
        setup("CUSTOMER","/api/viewings");
        assertTrue(interceptor.preHandle(request,response,new Object()));
        verify(request).setAttribute("authenticatedUserId",customer);
    }

    @Test
    void tenantMayUseViewingsApi() {
        setup("TENANT","/api/viewings/my");
        assertTrue(interceptor.preHandle(request,response,new Object()));
    }

    @Test
    void customerCannotEnterManagementApi() {
        setup("CUSTOMER","/api/rooms");
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
                ()->interceptor.preHandle(request,response,new Object()));
        assertEquals(403,ex.getStatusCode().value());
    }

    @Test
    void customerMayReadOwnRoomHoldsViaController() {
        setup("CUSTOMER", "/api/room-holds/my");
        assertTrue(interceptor.preHandle(request,response,new Object()));
    }

    @Test
    void tenantMayReadOwnRoomHold() {
        setup("TENANT", "/api/room-holds/"+UUID.randomUUID());
        assertTrue(interceptor.preHandle(request,response,new Object()));
    }

    @Test
    void ownerStillEntersManagementApi() {
        setup("OWNER","/api/properties");
        assertTrue(interceptor.preHandle(request,response,new Object()));
    }
}
