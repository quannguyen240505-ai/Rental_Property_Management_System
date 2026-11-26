package com.rental.property.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String name,
        String address,
        String description,
        String contactPhone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
