package com.rental.rental.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantProfileResponse(
        UUID id,
        UUID userId,
        String fullName,
        String phone,
        String email,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
