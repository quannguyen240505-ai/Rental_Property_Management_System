package com.rental.payment.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class AuthTokenIntrospector implements OpaqueTokenIntrospector {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenIntrospector.class);

    private static final Set<String> ROLES =
            Set.of("OWNER", "STAFF", "CUSTOMER", "TENANT");

    private final RestClient client;

    public AuthTokenIntrospector(String baseUrl, String serviceKey) {
        if (serviceKey == null
                || serviceKey.isBlank()
                || serviceKey.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "AUTH_INTERNAL_SERVICE_KEY must have at least 32 UTF-8 bytes and no whitespace");
        }

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));

        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Service-Key", serviceKey)
                .build();
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        IntrospectionResponse result;

        try {
            result = client.post()
                    .uri("/internal/auth/introspect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token))
                    .retrieve()
                    .onStatus(
                            status -> status.value() != 200,
                            (request, response) -> {
                                log.warn("Auth introspection returned HTTP {}; check Auth connectivity and internal credentials", response.getStatusCode().value());
                                throw new OAuth2IntrospectionException(
                                        "Auth introspection unavailable");
                            })
                    .body(IntrospectionResponse.class);
        } catch (RestClientException ex) {
            // Never log tokens, service keys, or response bodies.
            log.warn("Auth introspection connection, timeout, or response decoding failed");
            throw new OAuth2IntrospectionException(
                    "Không thể xác minh token qua Auth");
        }

        if (result == null || result.active() == null) {
            throw invalidResponse();
        }

        if (!result.active()) {
            throw new BadOpaqueTokenException(
                    "Token không hợp lệ hoặc đã bị thu hồi");
        }

        if (result.userId() == null
                || result.role() == null
                || !ROLES.contains(result.role())
                || result.expiresAt() == null
                || !result.expiresAt().isAfter(Instant.now())) {
            throw invalidResponse();
        }

        // Giữ Authentication.getName() là UUID cho các service hiện tại.
        String userId = result.userId().toString();

        return new DefaultOAuth2AuthenticatedPrincipal(
                userId,
                Map.of(
                        "sub", userId,
                        "userId", userId,
                        "role", result.role(),
                        "exp", result.expiresAt()),
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + result.role())));
    }

    private OAuth2IntrospectionException invalidResponse() {
        log.warn("Auth introspection returned an invalid active response");
        return new OAuth2IntrospectionException(
                "Invalid Auth introspection response");
    }

    public record IntrospectionResponse(
            Boolean active,
            UUID userId,
            String username,
            String role,
            Instant expiresAt) {
    }
}
