package com.rental.payment.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Identity and permission checked by Property; eligible may be false on a retry. */
public record VerifiedDepositHold(
        UUID holdId, UUID propertyId, UUID roomId, UUID customerId,
        String holdStatus, String roomStatus, OffsetDateTime expiresAt,
        Boolean eligible, String reason
) { }
