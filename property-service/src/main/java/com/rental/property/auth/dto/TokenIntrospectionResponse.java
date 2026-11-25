package com.rental.property.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenIntrospectionResponse(
        boolean active,
        UUID userId,
        String username,
        String role,
        Instant expiresAt
) {
}
