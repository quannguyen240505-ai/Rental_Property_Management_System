package com.rental.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

class ModuleSmokeTest {

    private AuthIntrospectionAuthenticationManager manager(
            HttpStatus status,
            String json) {

        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(status)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.APPLICATION_JSON_VALUE)
                                .body(json)
                                .build()
                ))
                .build();

        return new AuthIntrospectionAuthenticationManager(client);
    }

    @Test
    void validTokenShouldBeAuthenticated() {
        String json = """
                {
                  "active": true,
                  "userId": "%s",
                  "username": "owner",
                  "role": "OWNER",
                  "expiresAt": "%s"
                }
                """.formatted(
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(1800)
                );

        var result = manager(HttpStatus.OK, json)
                .authenticate(
                        new BearerTokenAuthenticationToken("valid-token"))
                .block();

        assertNotNull(result);
        assertTrue(result.isAuthenticated());

        assertTrue(
                result.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"))
        );
    }

    @Test
    void revokedTokenShouldBeRejected() {
        String json = """
                {
                  "active": false,
                  "userId": null,
                  "username": null,
                  "role": null,
                  "expiresAt": null
                }
                """;

        assertThrows(
                BadCredentialsException.class,
                () -> manager(HttpStatus.OK, json)
                        .authenticate(
                                new BearerTokenAuthenticationToken("revoked"))
                        .block()
        );
    }

    @Test
    void expiredTokenShouldBeRejected() {
        String json = """
                {
                  "active": true,
                  "userId": "%s",
                  "username": "owner",
                  "role": "OWNER",
                  "expiresAt": "%s"
                }
                """.formatted(
                        UUID.randomUUID(),
                        Instant.now().minusSeconds(60)
                );

        assertThrows(
                BadCredentialsException.class,
                () -> manager(HttpStatus.OK, json)
                        .authenticate(
                                new BearerTokenAuthenticationToken("expired"))
                        .block()
        );
    }

    @Test
    void invalidInternalServiceKeyShouldBeServiceFailure() {
        assertThrows(
                AuthenticationServiceException.class,
                () -> manager(
                        HttpStatus.UNAUTHORIZED,
                        "{\"code\":\"INVALID_SERVICE_KEY\"}"
                )
                        .authenticate(
                                new BearerTokenAuthenticationToken("token"))
                        .block()
        );
    }

    @Test
    void authServiceUnavailableShouldFailClosed() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request ->
                        Mono.error(new RuntimeException("Connection refused")))
                .build();

        var authManager =
                new AuthIntrospectionAuthenticationManager(client);

        assertThrows(
                AuthenticationServiceException.class,
                () -> authManager.authenticate(
                        new BearerTokenAuthenticationToken("token"))
                        .block()
        );
    }
}
