package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionRequest;
import com.rental.property.auth.dto.TokenIntrospectionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Calls the Auth Service's internal introspection endpoint.
 * Never decodes JWTs locally and never reads auth_db or JWT_SECRET.
 */
@Component
public class AuthIntrospectionClient {

    private final RestClient restClient;
    private final String introspectionUrl;
    private final String serviceKey;

    public AuthIntrospectionClient(
            @Value("${AUTH_INTROSPECTION_URL:http://localhost:8081/internal/auth/introspect}") String introspectionUrl,
            @Value("${AUTH_INTERNAL_SERVICE_KEY:}") String serviceKey
    ) {
        this.introspectionUrl = introspectionUrl;
        this.serviceKey = serviceKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public TokenIntrospectionResponse introspectBearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or invalid Bearer token");
        }

        String rawToken = authorizationHeader.substring(7).trim();
        if (rawToken.isEmpty() || rawToken.matches(".*\\s+.*")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid Bearer token");
        }

        // A missing service key must never permit a request through.
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is not configured");
        }

        final TokenIntrospectionResponse authResponse;
        try {
            authResponse = restClient.post()
                    .uri(introspectionUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Service-Key", serviceKey)
                    .body(new TokenIntrospectionRequest(rawToken))
                    .retrieve()
                    .body(TokenIntrospectionResponse.class);
        } catch (RestClientException ex) {
            // Includes connection errors, timeouts and wrong/missing service-key responses.
            // This is an upstream/internal failure; do not misreport it as an invalid user JWT.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication service unavailable", ex);
        }

        if (authResponse == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Invalid authentication service response");
        }
        if (!authResponse.active()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid, expired or revoked token");
        }
        if (authResponse.userId() == null
                || authResponse.role() == null
                || authResponse.role().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Incomplete authentication service response");
        }
        return authResponse;
    }
}
