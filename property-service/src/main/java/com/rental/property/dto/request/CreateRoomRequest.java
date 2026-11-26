package com.rental.property.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRoomRequest(
        UUID propertyId,
        String code,
        String title,
        String description,
        Integer floorNumber,
        BigDecimal areaM2,
        Long monthlyRent
) {
}
