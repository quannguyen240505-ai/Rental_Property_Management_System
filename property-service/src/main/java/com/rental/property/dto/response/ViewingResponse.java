package com.rental.property.dto.response;

import com.rental.property.entity.ViewingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ViewingResponse(
        UUID id, UUID roomId, UUID propertyId,
        UUID customerId, UUID staffId,
        ViewingStatus status, LocalDateTime startAt, LocalDateTime endAt,
        String resultNotes, LocalDateTime createdAt, LocalDateTime updatedAt
) { }
