package com.rental.auth.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{3,60}")
    String username,

    @NotBlank
    String password,

    @NotBlank
    @Size(max = 120)
    String fullName,

    @NotBlank
    @Email
    @Size(max = 160)
    String email,

    // Bắt buộc nhập số điện thoại Việt Nam khi đăng ký.
    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^0[0-9]{9}$",
        message = "Phone must contain 10 digits and start with 0"
    )
    String phone

) {}
