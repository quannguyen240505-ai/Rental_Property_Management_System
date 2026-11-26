package com.rental.rental.service;

import com.rental.rental.dto.DepositSummaryResponse;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractDepositServiceTest {

    private ContractDepositRepository depositRepository;
    private RentalContractRepository contractRepository;
    private ContractDepositService depositService;

    private UUID contractId;

    @BeforeEach
    void setUp() {
        depositRepository = mock(ContractDepositRepository.class);
        contractRepository = mock(RentalContractRepository.class);

        depositService = new ContractDepositService(
                depositRepository,
                contractRepository
        );

        contractId = UUID.randomUUID();

        RentalContract contract = new RentalContract();
        contract.setDepositRequired(2_000_000L);

        when(contractRepository.findById(contractId))
                .thenReturn(Optional.of(contract));
    }

    @Test
    void depositSummary_noDeposit() {
        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(0L);

        DepositSummaryResponse response =
                depositService.getDepositSummary(contractId);

        assertEquals(2_000_000L, response.depositRequired());
        assertEquals(0L, response.depositApplied());
        assertEquals(2_000_000L, response.depositRemaining());
    }

    @Test
    void depositSummary_partialDeposit() {
        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(500_000L);

        DepositSummaryResponse response =
                depositService.getDepositSummary(contractId);

        assertEquals(500_000L, response.depositApplied());
        assertEquals(1_500_000L, response.depositRemaining());
    }

    @Test
    void depositSummary_fullyPaid() {
        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(2_000_000L);

        DepositSummaryResponse response =
                depositService.getDepositSummary(contractId);

        assertEquals(2_000_000L, response.depositApplied());
        assertEquals(0L, response.depositRemaining());
    }

    @Test
    void depositSummary_contractNotFound() {
        when(contractRepository.findById(contractId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> depositService.getDepositSummary(contractId)
        );
    }
}
