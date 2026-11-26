package com.rental.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String role, UUID userId) {
}
