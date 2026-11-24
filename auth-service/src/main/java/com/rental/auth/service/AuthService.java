package com.rental.auth.service;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import com.rental.auth.enums.UserStatus;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final PasswordPolicy policy;
    private final String issuer;
    private final long expirationMinutes;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder, PasswordPolicy policy,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.expiration-minutes:240}") long expirationMinutes) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.policy = policy;
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        policy.validate(request.password());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) conflict("Username already exists");
        if (users.existsByEmailIgnoreCase(email)) conflict("Email already exists");
        AppUser user = new AppUser(username, passwords.encode(request.password()), request.fullName().trim(),
                email, request.phone(), Role.CUSTOMER);
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(this::badCredentials);
        if (user.getStatus() != UserStatus.ACTIVE || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw badCredentials();
        }
        return issueToken(user);
    }

    /** Both local login and Google login issue the SAME application JWT claims. */
    public LoginResponse issueToken(AppUser user) {
        if (user.getStatus() != UserStatus.ACTIVE) throw badCredentials();
        Instant now = Instant.now();
        Instant expires = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString()).issuedAt(now)
                .expiresAt(expires).claim("roles", List.of("ROLE_" + user.getRole().name()))
                .claim("username", user.getUsername()).claim("tv", user.getTokenVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new LoginResponse(token, "Bearer", expires, user.getRole().name(), user.getId());
    }

    @Transactional
    public void changePassword(UUID currentUserId, ChangePasswordRequest request) {
        AppUser user = users.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Current password is incorrect");
        }
        policy.validate(request.newPassword());
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED", "New password must be different");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.invalidateTokens();
    }

    @Transactional
    public void logoutAll(UUID currentUserId) {
        AppUser user = users.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        user.invalidateTokens();
    }

    private ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid username/password or locked account");
    }

    private void conflict(String message) {
        throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
