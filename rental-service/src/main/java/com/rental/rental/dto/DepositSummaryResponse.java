package com.rental.rental.dto;

import java.util.UUID;

public record DepositSummaryResponse(
        UUID contractId,
        Long depositRequired,
        Long depositApplied,
        Long depositRemaining
) {
}
