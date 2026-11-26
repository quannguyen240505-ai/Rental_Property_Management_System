package com.rental.property.dto.response;

import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Payment-facing, point-in-time Property eligibility. Not proof of payment. */
public record DepositEligibilityResponse(
        UUID holdId,
        UUID propertyId,
        UUID roomId,
        UUID customerId,
        RoomHoldStatus holdStatus,
        RoomStatus roomStatus,
        OffsetDateTime expiresAt,
        boolean eligible,
        String reason
) { }
