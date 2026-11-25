package com.rental.rental.service;

import java.util.UUID;

public interface ReservationVerificationClient {

    VerifiedReservation verifyReservation(UUID reservationId);

    record VerifiedReservation(
            UUID reservationId,
            UUID roomId,
            UUID userId,
            boolean valid
    ) {
    }
}
