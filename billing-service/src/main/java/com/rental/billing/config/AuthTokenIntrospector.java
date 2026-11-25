package com.rental.billing.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private static final Set<String> ROLES =
            Set.of("OWNER", "STAFF", "CUSTOMER", "TENANT");

    private final RestClient client;

    public AuthTokenIntrospector(String baseUrl, String serviceKey) {
        if (serviceKey == null
                || serviceKey.isBlank()
                || serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "AUTH_INTERNAL_SERVICE_KEY phải có ít nhất 32 bytes");
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
                                throw new OAuth2IntrospectionException(
                                        "Auth introspection không khả dụng");
                            })
                    .body(IntrospectionResponse.class);
        } catch (RestClientException ex) {
            // Không đưa token, service key hoặc response Auth vào lỗi.
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
        return new OAuth2IntrospectionException(
                "Response xác minh từ Auth không hợp lệ");
    }

    public record IntrospectionResponse(
            Boolean active,
            UUID userId,
            String username,
            String role,
            Instant expiresAt) {
    }
}
