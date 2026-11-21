package com.rental.property.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicRoomResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String code,
        String title,
        String description,
        Integer floorNumber,
        BigDecimal areaM2,
        Long monthlyRent,
        String contactPhone
) {
}
