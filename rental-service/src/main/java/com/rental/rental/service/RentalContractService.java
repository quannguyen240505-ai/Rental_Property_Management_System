package com.rental.rental.service;

import com.rental.rental.dto.CreateRentalContractRequest;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.entity.TenantProfile;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.repository.TenantProfileRepository;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.access.RentalContractAccessService;

import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.rental.rental.property.PropertyVerificationClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RentalContractService {

    private final RentalContractRepository contractRepository;
    private final TenantProfileRepository tenantRepository;
    private final PropertyVerificationClient propertyVerificationClient;
    private final RentalContractAccessService contractAccessService;

    public RentalContractService(
            RentalContractRepository contractRepository,
            TenantProfileRepository tenantRepository,
            PropertyVerificationClient propertyVerificationClient,
            RentalContractAccessService contractAccessService) {

        this.contractRepository = contractRepository;
        this.tenantRepository = tenantRepository;
        this.propertyVerificationClient = propertyVerificationClient;
        this.contractAccessService = contractAccessService;
    }

    @Transactional
    public RentalContractResponse createDraft(
            CreateRentalContractRequest request) {

        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalArgumentException(
                    "End date must be after start date");
        }

        TenantProfile tenant = tenantRepository
                .findById(request.tenantProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant profile not found"));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_OWNER".equals(authority.getAuthority()));
        if (!isOwner) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only OWNER can create rental contracts"
            );
        }
        String ownerUserId = authentication.getName();
        propertyVerificationClient.verifyRoomAccess(
                request.roomId(), ownerUserId
        );
        boolean hasOverlap = contractRepository.existsOverlappingContract(
                request.roomId(),
                request.startDate(),
                request.endDate(),
                List.of(ContractStatus.ACTIVE)
        );

        if (hasOverlap) {
            throw new ContractConflictException(
                    "Room already has an active contract in this period"
            );
        }

        RentalContract contract = new RentalContract();

        contract.setRoomId(request.roomId());
        contract.setTenantProfile(tenant);
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setRentAmount(request.rentAmount());
        contract.setDepositRequired(request.depositRequired());
        contract.setStatus(ContractStatus.DRAFT);

        RentalContract saved = contractRepository.save(contract);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RentalContractResponse getById(UUID id) {

        RentalContract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental contract not found"
                ));

        // Verify access before returning contract data
        contractAccessService.requireContractAccess(contract);

        return toResponse(contract);
    }


    @Transactional(readOnly = true)
    public List<RentalContractResponse> getAll() {

        return contractRepository.findAll()
                .stream()
                .filter(contract -> {
                    try {
                        contractAccessService.requireContractAccess(contract);
                        return true;
                    } catch (org.springframework.web.server.ResponseStatusException ex) {
                        if (ex.getStatusCode().value() == 403) {
                            return false;
                        }
                        throw ex;
                    }
                })
                .map(this::toResponse)
                .toList();
    }


    private RentalContractResponse toResponse(
            RentalContract contract) {

        return new RentalContractResponse(
                contract.getId(),
                contract.getRoomId(),
                contract.getTenantProfile().getId(),
                contract.getStatus().name(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getRentAmount(),
                contract.getDepositRequired());
    }
}
