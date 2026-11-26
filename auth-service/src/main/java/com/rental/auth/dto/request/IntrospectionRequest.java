package com.rental.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IntrospectionRequest(
        @NotBlank String token
) {
}
