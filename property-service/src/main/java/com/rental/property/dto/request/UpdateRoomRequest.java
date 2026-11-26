package com.rental.property.dto.request;

import java.math.BigDecimal;

public record UpdateRoomRequest(
        String code,
        String title,
        String description,
        Integer floorNumber,
        BigDecimal areaM2,
        Long monthlyRent
) {
}
