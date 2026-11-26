package com.rental.rental.property;

import java.util.UUID;

public record RoomContractEligibilityResponse(
        UUID roomId,
        UUID propertyId,
        String roomStatus,
        Boolean eligible,
        String reason,
        Boolean requiresHoldId,
        UUID holdId,
        String holdStatus,
        UUID customerId
) {
}
