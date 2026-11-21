package com.rental.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IntrospectionResponse(
        boolean active,
        UUID userId,
        String username,
        String role,
        Instant expiresAt
) {

    public static IntrospectionResponse inactive() {
        return new IntrospectionResponse(
                false, null, null, null, null
        );
    }
}
