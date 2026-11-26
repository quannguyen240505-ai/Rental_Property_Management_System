package com.rental.payment.integration;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Component
public class PropertyDepositHoldVerifier implements DepositHoldVerifier {
    private static final Logger log = LoggerFactory.getLogger(PropertyDepositHoldVerifier.class);
    private final RestClient client;

    public PropertyDepositHoldVerifier(
            @Value("${property.base-url:${PROPERTY_BASE_URL:http://localhost:8082}}") String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PROPERTY_BASE_URL must be an HTTP(S) origin, e.g. http://localhost:8082");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public VerifiedDepositHold verifyForCollection(UUID holdId, Authentication authentication) {
        if (!(authentication instanceof BearerTokenAuthentication bearer)
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "UNAUTHORIZED");
        }
        VerifiedDepositHold result;
        try {
            result = client.get()
                    .uri("/api/room-holds/{holdId}/deposit-eligibility", holdId)
                    .headers(headers -> headers.setBearerAuth(bearer.getToken().getTokenValue()))
                    .retrieve()
                    .onStatus(status -> status.value() != 200, (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 401) throw new ResponseStatusException(UNAUTHORIZED, "PROPERTY_REJECTED_TOKEN");
                        if (code == 403) throw new ResponseStatusException(FORBIDDEN, "PROPERTY_FORBIDDEN");
                        // Until Property exposes this route, a route-level 404 is also possible.
                        if (code == 404) throw new ResponseStatusException(NOT_FOUND, "HOLD_NOT_FOUND");
                        log.warn("Property deposit eligibility returned HTTP {}", code);
                        throw unavailable();
                    })
                    .body(VerifiedDepositHold.class);
        } catch (RestClientException ex) {
            log.warn("Property deposit eligibility connection, timeout, or decoding failed");
            throw unavailable();
        }
        if (result == null || !holdId.equals(result.holdId())
                || result.propertyId() == null || result.roomId() == null || result.customerId() == null
                || result.expiresAt() == null || result.eligible() == null
                || result.holdStatus() == null || result.roomStatus() == null
                || (!result.eligible() && (result.reason() == null || result.reason().isBlank()))) {
            log.warn("Property deposit eligibility returned an incomplete or mismatched response");
            throw unavailable();
        }
        return result;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(SERVICE_UNAVAILABLE, "PROPERTY_SERVICE_UNAVAILABLE");
    }
}
