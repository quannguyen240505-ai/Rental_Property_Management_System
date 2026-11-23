package com.rental.rental.service;

import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractActivationServiceTest {

    @Mock
    private RentalContractRepository contractRepository;

    @Mock
    private ContractDepositRepository depositRepository;

    @Mock
    private RoomActivationLockManager roomLockManager;

    @InjectMocks
    private ContractActivationService activationService;

    private UUID contractId;
    private RentalContract contract;


    @BeforeEach
    void setUp() {
        contractId = UUID.randomUUID();
        contract = new RentalContract();

        contract.setStatus(ContractStatus.DRAFT);
        contract.setRoomId(UUID.randomUUID());
        contract.setDepositRequired(1_000_000L);
    }


    @Test
    void activate_success() {
        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(1_000_000L);

        when(contractRepository.existsOverlappingContract(
                any(), any(), any(), eq(List.of(ContractStatus.ACTIVE))
        )).thenReturn(false);

        when(contractRepository.save(contract))
                .thenReturn(contract);

        RentalContract result = activationService.activate(contractId);

        assertEquals(ContractStatus.ACTIVE, result.getStatus());
        verify(contractRepository).save(contract);

        var order = inOrder(roomLockManager, contractRepository);

        order.verify(roomLockManager).lockRoom(contract.getRoomId());
        order.verify(contractRepository).existsOverlappingContract(
                any(), any(), any(), eq(List.of(ContractStatus.ACTIVE))
        );
    }

    @Test
    void activate_contractNotFound() {
        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> activationService.activate(contractId));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void activate_contractNotDraft() {
        contract.setStatus(ContractStatus.ACTIVE);

        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        assertThrows(ContractConflictException.class,
                () -> activationService.activate(contractId));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void activate_insufficientDeposit() {
        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(500_000L);

        assertThrows(ContractConflictException.class,
                () -> activationService.activate(contractId));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void activate_overlappingContract() {
        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(1_000_000L);

        when(contractRepository.existsOverlappingContract(
                any(), any(), any(), eq(List.of(ContractStatus.ACTIVE))
        )).thenReturn(true);

        assertThrows(ContractConflictException.class,
                () -> activationService.activate(contractId));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void activate_noDepositRecords() {
        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(null);

        assertThrows(ContractConflictException.class,
                () -> activationService.activate(contractId));

        verify(contractRepository, never()).save(any());
    }
}
