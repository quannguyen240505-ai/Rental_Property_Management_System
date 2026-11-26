package com.rental.rental.property;

import com.rental.rental.access.RentalPropertyAccessClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpPropertyVerificationClientTest {

    private RestTemplate restTemplate;
    private HttpPropertyVerificationClient client;

    private final String ownerUserId = "owner-test-id";
    private final String token = "test-jwt-token";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        RentalPropertyAccessClient accessClient =
                mock(RentalPropertyAccessClient.class);

        client = new HttpPropertyVerificationClient(
                accessClient,
                restTemplate,
                "http://localhost:8082"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        ownerUserId,
                        null,
                        List.of()
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

    @Test
    void getContractEligibility_success_forwardsJwt() {
        UUID roomId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        RoomContractEligibilityResponse expected =
                new RoomContractEligibilityResponse(
                        roomId,
                        propertyId,
                        "AVAILABLE",
                        true,
                        null,
                        false,
                        null,
                        null,
                        null
                );

        String expectedUrl =
                "http://localhost:8082/api/rooms/"
                        + roomId
                        + "/contract-eligibility";

        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RoomContractEligibilityResponse.class)
        )).thenReturn(ResponseEntity.ok(expected));

        RoomContractEligibilityResponse actual =
                client.getContractEligibility(
                        roomId,
                        ownerUserId
                );

        assertNotNull(actual);
        assertEquals(roomId, actual.roomId());
        assertEquals(propertyId, actual.propertyId());
        assertEquals("AVAILABLE", actual.roomStatus());
        assertTrue(actual.eligible());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<?>> captor =
                org.mockito.ArgumentCaptor.forClass(
                        HttpEntity.class
                );

        verify(restTemplate).exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                captor.capture(),
                eq(RoomContractEligibilityResponse.class)
        );

        assertEquals(
                "Bearer " + token,
                captor.getValue()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "403, FORBIDDEN",
            "404, NOT_FOUND",
            "503, SERVICE_UNAVAILABLE"
    })
    void getContractEligibility_httpError_mapsCorrectStatus(
            int upstreamStatus,
            HttpStatus expectedStatus
    ) {
        UUID roomId = UUID.randomUUID();

        String url = "http://localhost:8082/api/rooms/"
                + roomId + "/contract-eligibility";

        HttpStatus status = HttpStatus.valueOf(upstreamStatus);

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RoomContractEligibilityResponse.class)
        )).thenThrow(
                HttpClientErrorException.create(
                        status,
                        "Upstream error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.getContractEligibility(
                        roomId,
                        ownerUserId
                )
        );

        assertEquals(expectedStatus, exception.getStatusCode());
    }

    @Test
    void getContractEligibility_connectionFailure_returns503() {
        UUID roomId = UUID.randomUUID();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RoomContractEligibilityResponse.class)
        )).thenThrow(
                new ResourceAccessException("Connection refused")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.getContractEligibility(
                        roomId,
                        ownerUserId
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
    }

    @Test
    void getContractEligibility_nullResponse_returns503() {
        UUID roomId = UUID.randomUUID();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RoomContractEligibilityResponse.class)
        )).thenReturn(ResponseEntity.ok().build());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.getContractEligibility(
                        roomId, ownerUserId
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
    }

    @Test
    void getContractEligibility_wrongRoomId_returns503() {
        UUID requestedRoomId = UUID.randomUUID();
        UUID differentRoomId = UUID.randomUUID();

        RoomContractEligibilityResponse invalidResponse =
                new RoomContractEligibilityResponse(
                        differentRoomId,
                        UUID.randomUUID(),
                        "AVAILABLE",
                        true,
                        null,
                        false,
                        null,
                        null,
                        null
                );

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RoomContractEligibilityResponse.class)
        )).thenReturn(ResponseEntity.ok(invalidResponse));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.getContractEligibility(
                        requestedRoomId, ownerUserId
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
    }

}
