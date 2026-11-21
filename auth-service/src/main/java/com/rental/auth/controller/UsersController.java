package com.rental.auth.controller;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.enums.*;
import com.rental.auth.service.UserManagementService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UsersController {

	private final UserManagementService users;

	public UsersController(UserManagementService users) {
		this.users = users;
	}

	@GetMapping("/me")
	public UserResponse me(Authentication principal) {
		return users.me(UUID.fromString(principal.getName()));
	}

	@PatchMapping("/me")
	public UserResponse updateMe(Authentication principal, @Valid @RequestBody UpdateProfileRequest request) {
		return users.updateMe(UUID.fromString(principal.getName()), request);
	}

	@GetMapping
	@PreAuthorize("hasRole('OWNER')")
	public List<UserResponse> list(@RequestParam(required = false) Role role,
			@RequestParam(required = false) UserStatus status) {
		return users.list(role, status);
	}

	@GetMapping("/staff")
	@PreAuthorize("hasRole('OWNER')")
	public List<UserResponse> staff() {
		return users.staff();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public UserResponse get(@PathVariable UUID id) {
		return users.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('OWNER')")
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return users.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
		return users.update(id, request);
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER')")
	public UserResponse setStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
		return users.setStatus(id, request);
	}

	@PatchMapping("/{id}/role")
	@PreAuthorize("hasRole('OWNER')")
	public UserResponse setRole(Authentication principal, @PathVariable UUID id,
			@Valid @RequestBody RoleChangeRequest request) {
		return users.setRole(UUID.fromString(principal.getName()), id, request);
	}

	@GetMapping("/{id}/role")
	@PreAuthorize("hasRole('OWNER')")
	public Role getRole(@PathVariable UUID id) {
		return users.get(id).role();
	}

	@GetMapping("/{id}/role-history")
	@PreAuthorize("hasRole('OWNER')")
	public List<RoleHistoryResponse> roleHistory(@PathVariable UUID id) {
		return users.roleHistory(id);
	}
}
