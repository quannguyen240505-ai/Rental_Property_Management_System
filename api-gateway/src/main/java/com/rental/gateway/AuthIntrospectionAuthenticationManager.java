package com.rental.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Xác thực JWT thông qua Auth Service.
 *
 * Gateway không tự decode hoặc kiểm tra chữ ký JWT.
 * Auth Service quyết định token có còn hiệu lực hay không.
 */
public class AuthIntrospectionAuthenticationManager
        implements ReactiveAuthenticationManager {

    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient authClient;

    public AuthIntrospectionAuthenticationManager(WebClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {

        if (!(authentication instanceof BearerTokenAuthenticationToken bearer)) {
            return Mono.error(
                    new BadCredentialsException("Bearer token required"));
        }

        String rawToken = bearer.getToken();

        return authClient.post()
                .uri("/internal/auth/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new IntrospectionRequest(rawToken))

                .exchangeToMono(response -> {
                    // HTTP 401 từ Auth ở endpoint này là lỗi service key,
                    // không phải JWT người dùng không hợp lệ.
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.error(
                                new AuthenticationServiceException(
                                        "Auth introspection failed: HTTP "
                                                + response.statusCode().value()));
                    }

                    return response.bodyToMono(IntrospectionResponse.class)
                            .switchIfEmpty(Mono.error(
                                    new AuthenticationServiceException(
                                            "Empty Auth introspection response")));
                })

                // Giới hạn thời gian; không chờ Auth vô hạn.
                .timeout(AUTH_TIMEOUT)

                // Network error, timeout hoặc JSON lỗi -> 503.
                .onErrorMap(
                        ex -> !(ex instanceof AuthenticationException),
                        ex -> new AuthenticationServiceException(
                                "Auth Service is unavailable", ex)
                )

                .flatMap(this::toAuthentication);
    }

    private Mono<Authentication> toAuthentication(
            IntrospectionResponse response) {

        // Auth xác nhận token bị thu hồi, hết hạn hoặc không hợp lệ.
        if (!response.active()) {
            return Mono.error(
                    new BadCredentialsException(
                            "Invalid or revoked access token"));
        }

        // Không chấp nhận response active=true nhưng thiếu thông tin.
        if (response.userId() == null
                || response.role() == null
                || response.role().isBlank()
                || response.expiresAt() == null) {

            return Mono.error(
                    new AuthenticationServiceException(
                            "Incomplete Auth introspection response"));
        }

        if (!response.expiresAt().isAfter(Instant.now())) {
            return Mono.error(
                    new BadCredentialsException("Expired access token"));
        }

        String role = response.role();

        // Auth trả OWNER/STAFF/TENANT/CUSTOMER, không có ROLE_.
        // Spring Security dùng authority ROLE_OWNER, ROLE_STAFF...
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role)
        );

        Authentication result =
                UsernamePasswordAuthenticationToken.authenticated(
                        response.userId().toString(),
                        null,
                        authorities
                );

        return Mono.just(result);
    }

    // JSON request đúng contract của Auth.
    private record IntrospectionRequest(String token) {
    }

    // JSON response đúng contract của Auth.
    private record IntrospectionResponse(
            boolean active,
            UUID userId,
            String username,
            String role,
            Instant expiresAt
    ) {
    }
}
