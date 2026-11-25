package com.rental.property.dto.response;

import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record RoomHoldResponse(
        UUID id,
        UUID viewingId,
        UUID roomId,
        UUID propertyId,
        UUID customerId,
        UUID createdBy,
        String receiptReference,
        LocalDateTime expiresAt,
        RoomHoldStatus status,
        RoomStatus roomStatus,
        UUID approvedBy,
        LocalDateTime approvedAt,
        String verificationReference,
        UUID closedBy,
        LocalDateTime closedAt,
        String depositResolutionReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
