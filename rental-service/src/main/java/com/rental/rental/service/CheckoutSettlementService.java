package com.rental.rental.service;

import com.rental.rental.dto.CheckoutRequest;
import com.rental.rental.dto.CheckoutSettlementResponse;
import com.rental.rental.entity.CheckoutSettlement;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.CheckoutSettlementRepository;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class CheckoutSettlementService {
    private final RentalContractRepository contractRepository;
    private final ContractDepositRepository depositRepository;
    private final CheckoutSettlementRepository settlementRepository;

    public CheckoutSettlementService(
            RentalContractRepository contractRepository,
            ContractDepositRepository depositRepository,
            CheckoutSettlementRepository settlementRepository) {
        this.contractRepository = contractRepository;
        this.depositRepository = depositRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutSettlementResponse checkout(UUID contractId, CheckoutRequest request) {
        if (request == null || request.checkoutDate() == null || request.deductionAmount() == null) {
            throw new IllegalArgumentException("Checkout date and deduction amount are required");
        }
        if (request.deductionAmount() < 0) {
            throw new IllegalArgumentException("Deduction amount cannot be negative");
        }
        if (request.notes() != null && request.notes().length() > 1000) {
            throw new IllegalArgumentException("Notes cannot exceed 1000 characters");
        }

        // Row lock serializes concurrent checkout requests for the same contract.
        RentalContract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental contract not found"));
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ContractConflictException("Only ACTIVE contracts can be checked out");
        }
        if (request.checkoutDate().isBefore(contract.getStartDate())) {
            throw new IllegalArgumentException("Checkout date cannot precede contract start date");
        }

        Long sum = depositRepository.sumAppliedAmountByContractId(contractId);
        long applied = sum == null ? 0L : sum;
        if (request.deductionAmount() > applied) {
            throw new ContractConflictException("Deduction amount exceeds applied deposit");
        }

        CheckoutSettlement settlement = new CheckoutSettlement();
        settlement.setContract(contract);
        settlement.setCheckoutDate(request.checkoutDate());
        settlement.setAppliedDeposit(applied);
        settlement.setDeductionAmount(request.deductionAmount());
        settlement.setRefundDue(applied - request.deductionAmount());
        settlement.setNotes(request.notes());

        contract.setStatus(ContractStatus.ENDED);
        contractRepository.save(contract);
        CheckoutSettlement saved = settlementRepository.saveAndFlush(settlement);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CheckoutSettlementResponse getByContractId(UUID contractId) {
        CheckoutSettlement settlement = settlementRepository.findByContract_Id(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout settlement not found"));
        return toResponse(settlement);
    }

    private CheckoutSettlementResponse toResponse(CheckoutSettlement settlement) {
        return new CheckoutSettlementResponse(
                settlement.getId(),
                settlement.getContract().getId(),
                settlement.getContract().getStatus().name(),
                settlement.getCheckoutDate(),
                settlement.getAppliedDeposit(),
                settlement.getDeductionAmount(),
                settlement.getRefundDue(),
                settlement.getNotes()
        );
    }
}
