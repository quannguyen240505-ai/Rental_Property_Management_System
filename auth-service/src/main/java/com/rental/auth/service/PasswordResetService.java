
package com.rental.auth.service;

import com.rental.auth.dto.request.*;
import com.rental.auth.entity.AppUser;
import com.rental.auth.entity.PasswordResetToken;
import com.rental.auth.enums.UserStatus;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder passwords;
	private final PasswordPolicy policy;
	private final ResetLinkSender sender;

	public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder passwords,
			PasswordPolicy policy, ResetLinkSender sender) {
		this.users = users;
		this.tokens = tokens;
		this.passwords = passwords;
		this.policy = policy;
		this.sender = sender;
	}

	// Return the same response whether the email exists or not.
	@Transactional
	public void requestReset(ForgotPasswordRequest request) {
		users.findByEmailIgnoreCase(request.email().trim()).filter(u -> u.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> {

					byte[] randomBytes = new byte[32];
					RANDOM.nextBytes(randomBytes);

					String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

					tokens.save(new PasswordResetToken(user, sha256(raw), Instant.now().plusSeconds(900)));

					try {
						sender.send(user.getEmail(), raw);
					} catch (RuntimeException ex) {
						LOG.error("Password reset delivery failed (check mail service configuration)", ex);
					}
				});
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		policy.validate(request.newPassword());

		PasswordResetToken token = tokens.findByTokenHash(sha256(request.token())).orElseThrow(this::invalidToken);

		if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
			throw invalidToken();
		}

		AppUser user = token.getUser();

		if (user.getStatus() != UserStatus.ACTIVE || user.getTokenVersion() != token.getTokenVersion()) {
			throw invalidToken();
		}

		user.setPasswordHash(passwords.encode(request.newPassword()));

		user.invalidateTokens();
		token.markUsed();
	}

	private ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
				"Reset token is invalid, expired, or already used");
	}

	private static String sha256(String token) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));

			return HexFormat.of().formatHex(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
