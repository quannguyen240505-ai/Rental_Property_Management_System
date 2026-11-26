package com.rental.rental.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.UUID;

@Component
public class HttpRentalPropertyAccessClient
        implements RentalPropertyAccessClient {

    private final RestTemplate restTemplate;

    public HttpRentalPropertyAccessClient() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        this.restTemplate = new RestTemplate(factory);
    }

    // Constructor phục vụ Unit Tests
    HttpRentalPropertyAccessClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${app.property.base-url}")
    private String propertyBaseUrl;

    @Override
    public void requireRoomAccess(UUID roomId, String userId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || userId == null
                || !userId.equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        var attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Verified HTTP request required"
            );
        }

        HttpServletRequest request = servletAttributes.getRequest();

        String authorization =
                request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bearer token required"
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        String url = propertyBaseUrl
                + "/api/rooms/"
                + roomId;

        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Property authentication rejected"
                );
            }

            if (ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Not assigned to this property"
                );
            }

            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Property verification failed"
            );

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Property Service unavailable"
            );
        }
    }
}
