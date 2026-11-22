package com.rental.auth.repository;

import com.rental.auth.entity.RoleChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleChangeLogRepository extends JpaRepository<RoleChangeLog, UUID> {

	List<RoleChangeLog> findAllByTargetUserIdOrderByChangedAtDesc(UUID targetUserId);
}
