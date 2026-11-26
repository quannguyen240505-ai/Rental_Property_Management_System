package com.rental.rental.property;

import com.rental.rental.access.RentalPropertyAccessClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Component
@Primary
public class HttpPropertyVerificationClient
        implements PropertyVerificationClient {

    private final RentalPropertyAccessClient accessClient;
    private final RestTemplate restTemplate;
    private final String propertyBaseUrl;

    @Autowired
    public HttpPropertyVerificationClient(
            RentalPropertyAccessClient accessClient,
            @Value("${app.property.base-url:http://localhost:8082}")
            String propertyBaseUrl

    ) {
        this.accessClient = accessClient;
        this.propertyBaseUrl = propertyBaseUrl;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        this.restTemplate = new RestTemplate(factory);
    }
    // Constructor dành cho Unit Tests
    HttpPropertyVerificationClient(
            RentalPropertyAccessClient accessClient,
            RestTemplate restTemplate,
            String propertyBaseUrl
    ) {
        this.accessClient = accessClient;
        this.restTemplate = restTemplate;
        this.propertyBaseUrl = propertyBaseUrl;
    }

    @Override
    public void verifyRoomAccess(
            UUID roomId,
            String ownerUserId
    ) {
        accessClient.requireRoomAccess(roomId, ownerUserId);
    }

    @Override
    public RoomContractEligibilityResponse getContractEligibility(
            UUID roomId,
            String ownerUserId
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || ownerUserId == null
                || !ownerUserId.equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        var attributes =
                RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "HTTP request required"
            );
        }

        HttpServletRequest request = servlet.getRequest();
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
                + roomId
                + "/contract-eligibility";

        try {
            ResponseEntity<RoomContractEligibilityResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<Void>(headers),
                            RoomContractEligibilityResponse.class
                    );

            RoomContractEligibilityResponse result =
                    response.getBody();

            if (result == null
                    || result.roomId() == null
                    || !roomId.equals(result.roomId())
                    || result.propertyId() == null
                    || result.eligible() == null
                    || result.roomStatus() == null
                    || result.roomStatus().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Invalid Property eligibility response"
                );
            }

            return result;

        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();

            if (status == 401) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Property rejected authentication"
                );
            }

            if (status == 403) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Property access denied"
                );
            }

            if (status == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room or Property eligibility endpoint not found"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Property verification unavailable"
            );

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Property Service unavailable"
            );
        }
    }
}
