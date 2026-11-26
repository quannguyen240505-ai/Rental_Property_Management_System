package com.rental.property.dto.response;

import com.rental.property.entity.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        UUID propertyId,
        String code,
        String title,
        String description,
        Integer floorNumber,
        BigDecimal areaM2,
        Long monthlyRent,
        RoomStatus status,
        Boolean published,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
