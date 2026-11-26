package com.rental.property.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateRoomHoldRequest(
        UUID viewingId,
        String receiptReference,
        LocalDateTime expiresAt
) { }
