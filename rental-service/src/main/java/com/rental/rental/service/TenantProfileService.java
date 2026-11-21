package com.rental.rental.service;

import com.rental.rental.dto.CreateTenantProfileRequest;
import com.rental.rental.dto.TenantProfileResponse;
import com.rental.rental.entity.TenantProfile;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.TenantProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantProfileService {

    private final TenantProfileRepository repository;

    public TenantProfileService(TenantProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TenantProfileResponse create(CreateTenantProfileRequest request) {
        TenantProfile tenant = new TenantProfile();

        tenant.setFullName(request.fullName().trim());
        tenant.setPhone(request.phone());
        tenant.setEmail(request.email());
        tenant.setUserId(request.userId());

        TenantProfile saved = repository.saveAndFlush(tenant);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantProfileResponse getById(UUID id) {
        TenantProfile tenant = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant profile not found"
                ));

        return toResponse(tenant);
    }

    private TenantProfileResponse toResponse(TenantProfile tenant) {
        return new TenantProfileResponse(
                tenant.getId(),
                tenant.getUserId(),
                tenant.getFullName(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
