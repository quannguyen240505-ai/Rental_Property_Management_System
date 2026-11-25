package com.rental.rental.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTenantProfileRequest(
        UUID userId,

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @Size(max = 20)
        String phone,

        @Email(message = "Invalid email")
        @Size(max = 150)
        String email
) {}
