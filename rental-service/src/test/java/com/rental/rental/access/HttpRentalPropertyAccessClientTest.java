package com.rental.rental.access;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpRentalPropertyAccessClientTest {

    private RestTemplate restTemplate;
    private HttpRentalPropertyAccessClient client;

    private final UUID roomId = UUID.randomUUID();
    private final String userId = "staff-test-id";
    private final String token = "test-jwt-token";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        client = new HttpRentalPropertyAccessClient(restTemplate);

        ReflectionTestUtils.setField(
                client,
                "propertyBaseUrl",
                "http://localhost:8082"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_STAFF")
                        )
                )
        );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + token
        );

        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private String roomUrl() {
        return "http://localhost:8082/api/rooms/" + roomId;
    }

    @Test
    void validRoomAccessIsAllowed() {
        when(restTemplate.exchange(
                eq(roomUrl()),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        assertDoesNotThrow(
                () -> client.requireRoomAccess(roomId, userId)
        );

        verify(restTemplate).exchange(
                eq(roomUrl()),
                eq(HttpMethod.GET),
                argThat(entity ->
                        "Bearer ".concat(token).equals(
                                ((HttpEntity<?>) entity)
                                        .getHeaders()
                                        .getFirst(HttpHeaders.AUTHORIZATION)
                        )
                ),
                eq(String.class)
        );
    }

    @Test
    void propertyReturns401() {
        mockHttpError(HttpStatus.UNAUTHORIZED);

        assertStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void staffNotAssignedReturns403() {
        mockHttpError(HttpStatus.FORBIDDEN);

        assertStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void roomNotFoundReturns404() {
        mockHttpError(HttpStatus.NOT_FOUND);

        assertStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void propertyServerErrorReturns503() {
        when(restTemplate.exchange(
                eq(roomUrl()),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(
                new HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
        );

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void propertyConnectionFailureReturns503() {
        when(restTemplate.exchange(
                eq(roomUrl()),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(
                new ResourceAccessException(
                        "Connection refused"
                )
        );

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void differentUserIdIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.requireRoomAccess(
                        roomId,
                        "another-user-id"
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(restTemplate);
    }

    private void mockHttpError(HttpStatus status) {
        when(restTemplate.exchange(
                eq(roomUrl()),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(
                new HttpClientErrorException(status)
        );
    }

    private void assertStatus(HttpStatus expectedStatus) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.requireRoomAccess(roomId, userId)
        );

        assertEquals(
                expectedStatus,
                exception.getStatusCode()
        );
    }
}
