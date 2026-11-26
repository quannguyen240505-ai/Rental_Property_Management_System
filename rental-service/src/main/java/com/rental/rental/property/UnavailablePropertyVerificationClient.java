package com.rental.rental.property;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class UnavailablePropertyVerificationClient
        implements PropertyVerificationClient {

    @Override
    public void verifyRoomAccess(UUID roomId, String ownerUserId) {
        throw unavailable();
    }

    @Override
    public RoomContractEligibilityResponse getContractEligibility(
            UUID roomId,
            String ownerUserId
    ) {
        throw unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Property verification is not yet available"
        );
    }
}
