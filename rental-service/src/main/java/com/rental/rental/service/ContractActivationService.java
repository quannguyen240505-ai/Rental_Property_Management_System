package com.rental.rental.service;

import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.access.RentalContractAccessService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.UUID;

@Service
public class ContractActivationService {

    private final RentalContractRepository contractRepository;
    private final ContractDepositRepository depositRepository;
    private final RoomActivationLockManager roomLockManager;
    private final RentalContractAccessService contractAccessService;

    public ContractActivationService(
            RentalContractRepository contractRepository,
            ContractDepositRepository depositRepository,
            RoomActivationLockManager roomLockManager,
            RentalContractAccessService contractAccessService
    ) {
        this.contractRepository = contractRepository;
        this.depositRepository = depositRepository;
        this.roomLockManager = roomLockManager;
        this.contractAccessService = contractAccessService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RentalContract activate(UUID contractId) {

        RentalContract contract = contractRepository
                .findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental contract not found"
                ));
        // Verify permission before activating the contract.
        contractAccessService.requireContractAccess(contract);

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new ContractConflictException(
                    "Only DRAFT contracts can be activated"
            );
        }
        roomLockManager.lockRoom(contract.getRoomId());

        Long applied = depositRepository
                .sumAppliedAmountByContractId(contractId);

        if (applied == null) {
            applied = 0L;
        }

        if (applied < contract.getDepositRequired()) {
            throw new ContractConflictException(
                    "Contract deposit is not fully paid"
            );
        }

        boolean hasOverlap = contractRepository
                .existsOverlappingContract(
                        contract.getRoomId(),
                        contract.getStartDate(),
                        contract.getEndDate(),
                        List.of(ContractStatus.ACTIVE)
                );

        if (hasOverlap) {
            throw new ContractConflictException(
                    "Room already has an ACTIVE contract in this period"
            );
        }

        contract.setStatus(ContractStatus.ACTIVE);

        return contractRepository.save(contract);
    }
}
