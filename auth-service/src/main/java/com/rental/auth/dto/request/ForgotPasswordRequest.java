package com.rental.auth.dto.request;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
