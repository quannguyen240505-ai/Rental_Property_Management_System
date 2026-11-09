package com.rental.rental.service;

import com.rental.rental.dto.CreateRentalContractRequest;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.entity.TenantProfile;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.repository.TenantProfileRepository;
import com.rental.rental.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RentalContractService {

    private final RentalContractRepository contractRepository;
    private final TenantProfileRepository tenantRepository;

    public RentalContractService(
            RentalContractRepository contractRepository,
            TenantProfileRepository tenantRepository) {

        this.contractRepository = contractRepository;
        this.tenantRepository = tenantRepository;
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
                        "Rental contract not found"));

        return toResponse(contract);
    }

    @Transactional(readOnly = true)
    public List<RentalContractResponse> getAll() {

        return contractRepository.findAll()
                .stream()
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
