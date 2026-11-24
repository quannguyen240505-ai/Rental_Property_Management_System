package com.rental.auth.dto.response;

import com.rental.auth.entity.RoleChangeLog;
import com.rental.auth.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record RoleHistoryResponse(UUID id, UUID targetUserId, UUID actorUserId, Role previousRole, Role newRole,
		String reason, String contractReference, Instant changedAt) {
	public static RoleHistoryResponse from(RoleChangeLog entry) {
		return new RoleHistoryResponse(entry.getId(), entry.getTargetUserId(), entry.getActorUserId(),
				entry.getPreviousRole(), entry.getNewRole(), entry.getReason(), entry.getContractReference(),
				entry.getChangedAt());
	}
}
