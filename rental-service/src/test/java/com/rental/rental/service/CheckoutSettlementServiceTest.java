package com.rental.rental.service;

import com.rental.rental.dto.CheckoutRequest;
import com.rental.rental.entity.CheckoutSettlement;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.CheckoutSettlementRepository;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.access.RentalContractAccessService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutSettlementServiceTest {
    @Mock RentalContractRepository contracts;
    @Mock ContractDepositRepository deposits;
    @Mock CheckoutSettlementRepository settlements;
    @Mock
    RentalContractAccessService contractAccessService;

    private CheckoutSettlementService service;
    private RentalContract contract;
    private UUID contractId;

    @BeforeEach
    void init() {
        service = new CheckoutSettlementService(
                contracts,
                deposits,
                settlements,
                contractAccessService
        );
        contractId = UUID.randomUUID();
        contract = mock(RentalContract.class);
    }

    private CheckoutRequest request(long deduction) {
        return new CheckoutRequest(LocalDate.of(2027, 1, 20), deduction, "Inspection complete");
    }

    @Test
    void checkout_successRecordsRefundAndEndsContract() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE, ContractStatus.ENDED);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(contract.getId()).thenReturn(contractId);
        when(deposits.sumAppliedAmountByContractId(contractId)).thenReturn(1_000_000L);
        when(settlements.saveAndFlush(any(CheckoutSettlement.class)))
                .thenAnswer(call -> call.getArgument(0));

        var result = service.checkout(contractId, request(200_000));

        assertEquals(800_000L, result.refundDue());
        assertEquals(200_000L, result.deductionAmount());
        assertEquals("ENDED", result.contractStatus());
        verify(contract).setStatus(ContractStatus.ENDED);
        verify(contracts).save(contract);
        verify(settlements).saveAndFlush(any(CheckoutSettlement.class));
    }

    @Test
    void checkout_missingContract() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.checkout(contractId, request(0)));
    }

    @Test
    void checkout_rejectsDraft() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.DRAFT);
        assertThrows(ContractConflictException.class,
                () -> service.checkout(contractId, request(0)));
        verifyNoInteractions(settlements);
    }

    @Test
    void checkout_rejectsSecondRequest() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ENDED);
        assertThrows(ContractConflictException.class,
                () -> service.checkout(contractId, request(0)));
    }

    @Test
    void checkout_rejectsDeductionLargerThanDeposit() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(deposits.sumAppliedAmountByContractId(contractId)).thenReturn(100L);
        assertThrows(ContractConflictException.class,
                () -> service.checkout(contractId, request(101)));
        verifyNoInteractions(settlements);
    }

    @Test
    void checkout_rejectsBeforeStartDate() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2027, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(contractId, request(0)));
    }

    @Test
    void checkout_rejectsNegativeDeduction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(contractId, request(-1)));
        verifyNoInteractions(contracts);
    }

    @Test
    void checkout_noDepositMeansZeroRefund() {
        when(contracts.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE, ContractStatus.ENDED);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(contract.getId()).thenReturn(contractId);
        when(deposits.sumAppliedAmountByContractId(contractId)).thenReturn(null);
        when(settlements.saveAndFlush(any(CheckoutSettlement.class)))
                .thenAnswer(call -> call.getArgument(0));
        assertEquals(0L, service.checkout(contractId, request(0)).refundDue());
    }
}
