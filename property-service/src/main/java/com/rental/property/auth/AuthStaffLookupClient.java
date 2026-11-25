package com.rental.property.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/** For owner-initiated staff assignment, forward the owner JWT to Auth /api/users/{id}. */
@Component
public class AuthStaffLookupClient {
    private final String usersUrl;
    private final RestClient client;

    public AuthStaffLookupClient(@Value("${AUTH_USERS_BASE_URL:http://localhost:8081/api/users}") String usersUrl) {
        this.usersUrl = usersUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public record AuthUser(UUID id, String role, String status) { }

    public void requireActiveStaff(UUID staffId, String authorizationHeader) {
        if (staffId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "staffId is required");
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing owner token");
        }
        final AuthUser user;
        try {
            user = client.get().uri(usersUrl + "/" + staffId)
                    .header("Authorization", authorizationHeader)
                    .retrieve().body(AuthUser.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Auth user lookup unavailable", ex);
        }
        if (user == null || user.id() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Incomplete Auth user response");
        }
        if (!staffId.equals(user.id()) || !"STAFF".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User does not have the STAFF role");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Staff account is not ACTIVE");
        }
    }
}
