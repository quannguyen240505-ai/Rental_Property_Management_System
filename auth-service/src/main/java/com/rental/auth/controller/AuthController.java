package com.rental.auth.controller;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.service.AuthService;
import com.rental.auth.service.PasswordResetService;

import jakarta.validation.Valid;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;
	private final PasswordResetService resets;

	public AuthController(AuthService auth, PasswordResetService resets) {
		this.auth = auth;
		this.resets = resets;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return auth.register(request);
	}

	@PostMapping("/logout")
	public MessageResponse logout(Authentication principal) {
		auth.logoutAll(UUID.fromString(principal.getName()));

		return new MessageResponse("All Auth Service sessions invalidated");
	}

	@PostMapping("/change-password")
	public MessageResponse changePassword(Authentication principal, @Valid @RequestBody ChangePasswordRequest request) {
		auth.changePassword(UUID.fromString(principal.getName()), request);

		return new MessageResponse("Password changed; please sign in again");
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		resets.requestReset(request);

		return new MessageResponse("If the account exists, password reset instructions will be sent");
	}

	@PostMapping("/reset-password")
	public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		resets.resetPassword(request);

		return new MessageResponse("Password changed; please sign in again");
	}
}
