package com.rental.property.dto.response;

import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import java.util.UUID;

/** Point-in-time room/hold eligibility for creating a Rental DRAFT contract. */
public record ContractEligibilityResponse(
        UUID roomId,
        UUID propertyId,
        RoomStatus roomStatus,
        boolean eligible,
        String reason,
        boolean requiresHoldId,
        UUID holdId,
        RoomHoldStatus holdStatus,
        UUID customerId
) { }
