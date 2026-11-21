package com.rental.auth.dto.response;

import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import com.rental.auth.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String fullName, String email, String phone, Role role,
		UserStatus status, Instant createdAt, Instant updatedAt) {
	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getPhone(),
				user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
	}
}
