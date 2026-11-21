package com.rental.auth.service;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.entity.*;
import com.rental.auth.enums.*;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.*;

import java.util.*;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

	private final UserRepository users;
	private final RoleChangeLogRepository logs;
	private final PasswordEncoder passwords;
	private final PasswordPolicy passwordPolicy;
	private final RolePolicy rolePolicy;
	private final boolean manualRoleConfirmation;

	public UserManagementService(UserRepository users, RoleChangeLogRepository logs, PasswordEncoder passwords,
			PasswordPolicy passwordPolicy, RolePolicy rolePolicy,
			@Value("${app.role-management.manual-confirmation-enabled:false}") boolean manualRoleConfirmation) {
		this.users = users;
		this.logs = logs;
		this.passwords = passwords;
		this.passwordPolicy = passwordPolicy;
		this.rolePolicy = rolePolicy;
		this.manualRoleConfirmation = manualRoleConfirmation;
	}

	@Transactional(readOnly = true)
	public UserResponse me(UUID id) {
		return UserResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public UserResponse get(UUID id) {
		return UserResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public List<UserResponse> list(Role role, UserStatus status) {
		Stream<AppUser> stream = users.findAll().stream();

		if (role != null) {
			stream = stream.filter(u -> u.getRole() == role);
		}

		if (status != null) {
			stream = stream.filter(u -> u.getStatus() == status);
		}

		return stream.sorted(Comparator.comparing(AppUser::getCreatedAt).reversed()).map(UserResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<UserResponse> staff() {
		return users.findAllByRole(Role.STAFF).stream().map(UserResponse::from).toList();
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {

		if (request.role() != Role.STAFF) {
			throw bad("OWNER can create STAFF accounts only. " + "CUSTOMER must register, and TENANT "
					+ "requires a verified rental contract.");
		}

		passwordPolicy.validate(request.password());

		String username = request.username().trim().toLowerCase(Locale.ROOT);

		String email = normalizeEmail(request.email());

		assertUnique(username, email, null);

		AppUser user = new AppUser(username, passwords.encode(request.password()), request.fullName().trim(), email,
				request.phone(), request.role());

		return UserResponse.from(users.save(user));
	}

	@Transactional
	public UserResponse update(UUID target, UpdateUserRequest request) {
		AppUser user = find(target);

		if (user.getRole() == Role.OWNER) {
			throw bad("OWNER profile cannot be edited by this admin endpoint");
		}

		applyDetails(user, request.fullName(), request.email(), request.phone(), true);

		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse updateMe(UUID current, UpdateProfileRequest request) {
		AppUser user = find(current);

		applyDetails(user, request.fullName(), request.email(), request.phone(), false);

		return UserResponse.from(user);
	}

	private void applyDetails(AppUser user, String name, String email, String phone, boolean fullUpdate) {
		if (fullUpdate || name != null) {
			if (name == null || name.isBlank()) {
				throw bad("Full name is required");
			}

			user.setFullName(name.trim());
		}

		if (fullUpdate || email != null) {
			String normalized = normalizeEmail(email);

			if (normalized != null && users.findByEmailIgnoreCase(normalized)
					.filter(u -> !u.getId().equals(user.getId())).isPresent()) {
				throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Email already exists");
			}

			user.setEmail(normalized);
		}

		if (fullUpdate || phone != null) {
			user.setPhone(phone);
		}
	}

	@Transactional
	public UserResponse setStatus(UUID target, UserStatusRequest request) {
		AppUser user = find(target);

		if (user.getRole() == Role.OWNER) {
			throw bad("OWNER status cannot be changed");
		}

		if (user.getStatus() != request.status()) {
			user.setStatus(request.status());
			user.invalidateTokens();
		}

		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse setRole(UUID actor, UUID target, RoleChangeRequest request) {
		AppUser user = find(target);

		rolePolicy.validate(user.getRole(), request.role(), request.contractReference(),
				request.contractStatusConfirmed(), manualRoleConfirmation);

		Role old = user.getRole();

		user.setRole(request.role());
		user.invalidateTokens();

		logs.save(new RoleChangeLog(user.getId(), actor, old, request.role(), request.reason().trim(),
				request.contractReference()));

		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public List<RoleHistoryResponse> roleHistory(UUID target) {
		find(target);

		return logs.findAllByTargetUserIdOrderByChangedAtDesc(target).stream().map(RoleHistoryResponse::from).toList();
	}

	private AppUser find(UUID id) {
		return users.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
	}

	private String normalizeEmail(String email) {
		return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private void assertUnique(String username, String email, UUID current) {
		if (users.findByUsernameIgnoreCase(username).filter(u -> !u.getId().equals(current)).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Username already exists");
		}

		if (email != null && users.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(current)).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Email already exists");
		}
	}

	private ApiException bad(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
	}
}
