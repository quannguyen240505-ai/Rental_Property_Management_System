package com.rental.rental.service;

import com.rental.rental.dto.DepositSummaryResponse;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.access.RentalContractAccessService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContractDepositService {

    private final ContractDepositRepository depositRepository;
    private final RentalContractRepository contractRepository;
    private final RentalContractAccessService contractAccessService;

    public ContractDepositService(
            ContractDepositRepository depositRepository,
            RentalContractRepository contractRepository,
            RentalContractAccessService contractAccessService
    ) {
        this.depositRepository = depositRepository;
        this.contractRepository = contractRepository;
        this.contractAccessService = contractAccessService;
    }

    @Transactional(readOnly = true)
    public DepositSummaryResponse getDepositSummary(UUID contractId) {

        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental contract not found"
                ));
        contractAccessService.requireContractAccess(contract);

        Long applied = depositRepository
                .sumAppliedAmountByContractId(contractId);

        if (applied == null) {
            applied = 0L;
        }

        Long required = contract.getDepositRequired();

        Long remaining = Math.max(0L, required - applied);

        return new DepositSummaryResponse(
                contractId,
                required,
                applied,
                remaining
        );
    }
}
