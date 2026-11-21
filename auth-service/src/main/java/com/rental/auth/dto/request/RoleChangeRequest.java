
package com.rental.auth.dto.request;

import com.rental.auth.enums.Role;
import jakarta.validation.constraints.*;

public record RoleChangeRequest(@NotNull Role role,

		@NotBlank @Size(max = 500) String reason,

		@Size(max = 100) String contractReference,

		boolean contractStatusConfirmed) {
}
