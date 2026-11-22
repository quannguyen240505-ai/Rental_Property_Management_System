package com.rental.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", indexes = {
		@Index(name = "idx_password_reset_token_hash", columnList = "token_hash", unique = true) })
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "token_version", nullable = false)
	private long tokenVersion;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected PasswordResetToken() {
	}

	public PasswordResetToken(AppUser user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.tokenVersion = user.getTokenVersion();
	}

	public AppUser getUser() {
		return user;
	}

	public long getTokenVersion() {
		return tokenVersion;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public void markUsed() {
		usedAt = Instant.now();
	}
}
