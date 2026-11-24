package com.rental.auth.dto.request;

import com.rental.auth.enums.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,60}") String username,

		@NotBlank String password,

		@NotBlank @Size(max = 120) String fullName,

		@Email @Size(max = 160) String email,

		@Size(max = 25) String phone,

		@NotNull Role role) {
}
