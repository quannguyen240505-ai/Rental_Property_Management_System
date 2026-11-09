package com.rental.rental.repository;

import com.rental.rental.entity.TenantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantProfileRepository
        extends JpaRepository<TenantProfile, UUID> {
}
