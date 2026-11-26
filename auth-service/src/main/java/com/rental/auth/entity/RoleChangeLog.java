package com.rental.auth.entity;

import com.rental.auth.enums.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_change_logs")
public class RoleChangeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "target_user_id", nullable = false)
	private UUID targetUserId;

	@Column(name = "actor_user_id", nullable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_role", nullable = false, length = 20)
	private Role previousRole;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_role", nullable = false, length = 20)
	private Role newRole;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "contract_reference", length = 100)
	private String contractReference;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt = Instant.now();

	protected RoleChangeLog() {
	}

	public RoleChangeLog(UUID targetUserId, UUID actorUserId, Role previousRole, Role newRole, String reason,
			String contractReference) {
		this.targetUserId = targetUserId;
		this.actorUserId = actorUserId;
		this.previousRole = previousRole;
		this.newRole = newRole;
		this.reason = reason;
		this.contractReference = contractReference;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTargetUserId() {
		return targetUserId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public Role getPreviousRole() {
		return previousRole;
	}

	public Role getNewRole() {
		return newRole;
	}

	public String getReason() {
		return reason;
	}

	public String getContractReference() {
		return contractReference;
	}

	public Instant getChangedAt() {
		return changedAt;
	}
}
