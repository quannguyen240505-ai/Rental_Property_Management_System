package com.rental.auth.dto.request;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(@Size(max = 120) String fullName,

		@Email @Size(max = 160) String email,

		@Size(max = 25) String phone) {
}
