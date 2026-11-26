package com.rental.rental.property;

import java.util.UUID;

public interface PropertyVerificationClient {

    void verifyRoomAccess(UUID roomId, String ownerUserId);

    RoomContractEligibilityResponse getContractEligibility(
            UUID roomId,
            String ownerUserId
    );
}
