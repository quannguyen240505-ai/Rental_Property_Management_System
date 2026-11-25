package com.rental.rental.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AuthIntrospectionClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.auth.introspect-url}")
    private String introspectUrl;

    @Value("${app.auth.service-key}")
    private String serviceKey;

    public AuthUser introspect(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Key", serviceKey);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("token", token),
                headers
        );

        try {
            ResponseEntity<AuthUser> response = restTemplate.exchange(
                    introspectUrl,
                    HttpMethod.POST,
                    request,
                    AuthUser.class
            );

            AuthUser user = response.getBody();
            if (user == null) {
                throw new AuthServiceUnavailableException();
            }

            return user;

        } catch (HttpClientErrorException.Unauthorized e) {
            // X-Service-Key bị sai hoặc thiếu
            throw new AuthServiceUnavailableException();

        } catch (RestClientException e) {
            // Auth timeout hoặc không kết nối được
            throw new AuthServiceUnavailableException();
        }
    }

    public record AuthUser(
            boolean active,
            String userId,
            String username,
            String role,
            String expiresAt
    ) {}

    public static class AuthServiceUnavailableException
            extends RuntimeException {

        public AuthServiceUnavailableException() {
            super("Auth Service unavailable");
        }
    }
}
