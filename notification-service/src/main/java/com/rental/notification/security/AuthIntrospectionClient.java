package com.rental.notification.security;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AuthIntrospectionClient {
	private static final Set<String> ROLES = Set.of("OWNER", "STAFF", "TENANT", "CUSTOMER");
	private final RestClient http;

	public AuthIntrospectionClient(@Value("${app.auth.base-url}") String authBaseUrl,
			@Value("${app.auth.internal-service-key}") String serviceKey) {
		if (serviceKey == null || serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("AUTH_INTERNAL_SERVICE_KEY must be at least 32 UTF-8 bytes");
		}
		HttpClient jdkClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		this.http = RestClient.builder().baseUrl(authBaseUrl).defaultHeader("X-Service-Key", serviceKey)
				.requestFactory(requestFactory).build();
	}

	public Identity verify(String rawJwt) {
		final IntrospectionResponse response;
		try {
			response = http.post().uri("/internal/auth/introspect").body(new IntrospectionRequest(rawJwt)).retrieve()
					.body(IntrospectionResponse.class);
		} catch (RestClientException exception) {
			throw new AuthUnavailableException(exception);
		}

		if (response == null) {
			throw new AuthUnavailableException(null);
		}
		if (!response.active()) {
			return null;
		}
		if (response.userId() == null || response.role() == null || !ROLES.contains(response.role())
				|| response.expiresAt() == null || !response.expiresAt().isAfter(Instant.now())) {
			return null;
		}
		return new Identity(response.userId(), response.role());
	}

	public record Identity(UUID userId, String role) {
	}

	public record IntrospectionRequest(String token) {
	}

	public record IntrospectionResponse(boolean active, UUID userId, String username, String role, Instant expiresAt) {
	}

	public static class AuthUnavailableException extends RuntimeException {
		public AuthUnavailableException(Throwable cause) {
			super("Auth Service unavailable", cause);
		}
	}
}
