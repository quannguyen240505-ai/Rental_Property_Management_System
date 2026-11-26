package com.rental.payment.integration;

import java.util.UUID;

import org.springframework.security.core.Authentication;

public interface DepositHoldVerifier {

    VerifiedDepositHold verifyForCollection(
            UUID holdId,
            Authentication authentication);
}