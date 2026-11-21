package com.rental.auth.dto.request;

import com.rental.auth.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {
}
