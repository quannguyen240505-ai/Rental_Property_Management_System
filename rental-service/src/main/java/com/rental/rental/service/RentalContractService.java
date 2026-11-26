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
import com.rental.rental.property.RoomContractEligibilityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        RoomContractEligibilityResponse eligibility =
                propertyVerificationClient.getContractEligibility(
                        request.roomId(),
                        ownerUserId
                );

        validateContractEligibility(request, tenant, eligibility);
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
        contract.setHoldId(request.holdId());
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
                contract.getDepositRequired(),
                contract.getHoldId()
        );
    }

    private void validateContractEligibility(
            CreateRentalContractRequest request,
            TenantProfile tenant,
            RoomContractEligibilityResponse eligibility
    ) {
        if (eligibility == null
                || eligibility.roomId() == null
                || !request.roomId().equals(eligibility.roomId())
                || eligibility.propertyId() == null
                || eligibility.roomStatus() == null
                || eligibility.eligible() == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Invalid Property eligibility response"
            );
        }

        if (!eligibility.eligible()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room is not eligible for contract creation"
            );
        }

        switch (eligibility.roomStatus()) {
            case "AVAILABLE" -> {
                if (request.holdId() != null
                        || Boolean.TRUE.equals(eligibility.requiresHoldId())
                        || eligibility.holdId() != null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "AVAILABLE room must not use a Hold"
                    );
                }
            }

            case "RESERVED" -> {
                if (!Boolean.TRUE.equals(eligibility.requiresHoldId())
                        || request.holdId() == null
                        || eligibility.holdId() == null
                        || !request.holdId().equals(eligibility.holdId())
                        || !"APPROVED".equals(eligibility.holdStatus())
                        || eligibility.customerId() == null
                        || tenant.getUserId() == null
                        || !tenant.getUserId().equals(eligibility.customerId())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Approved Hold and matching customer required"
                    );
                }
            }

            case "RESERVED_PENDING", "OCCUPIED" ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Room is not available for contract creation"
                    );

            default -> throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unknown room status from Property Service"
            );
        }
    }

}
