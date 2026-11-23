package com.rental.rental.service;

import com.rental.rental.entity.*;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DepositApplicationServiceTest {

    private ContractDepositRepository depositRepository;
    private RentalContractRepository contractRepository;
    private PaymentVerificationClient paymentClient;
    private ReservationVerificationClient reservationClient;

    private DepositApplicationService service;

    private UUID contractId;
    private UUID receiptId;
    private UUID reservationId;
    private UUID roomId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        depositRepository = mock(ContractDepositRepository.class);
        contractRepository = mock(RentalContractRepository.class);
        paymentClient = mock(PaymentVerificationClient.class);
        reservationClient = mock(ReservationVerificationClient.class);

        service = new DepositApplicationService(
                depositRepository,
                contractRepository,
                paymentClient,
                reservationClient
        );

        contractId = UUID.randomUUID();
        receiptId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        userId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setUserId(userId);

        RentalContract contract = new RentalContract();
        contract.setRoomId(roomId);
        contract.setTenantProfile(tenant);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setDepositRequired(2_000_000L);

        when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(Optional.of(contract));

        when(paymentClient.verifyReceipt(receiptId))
                .thenReturn(new PaymentVerificationClient.VerifiedPayment(
                        receiptId,
                        reservationId,
                        500_000L,
                        true
                ));

        when(reservationClient.verifyReservation(reservationId))
                .thenReturn(
                        new ReservationVerificationClient.VerifiedReservation(
                                reservationId,
                                roomId,
                                userId,
                                true
                        )
                );

        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(0L);

        when(depositRepository.save(any(ContractDeposit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void applyReceipt_success() {
        ContractDeposit result =
                service.applyVerifiedReceipt(contractId, receiptId);

        assertEquals(receiptId, result.getSourceReceiptId());
        assertEquals(500_000L, result.getAppliedAmount());

        verify(depositRepository).save(any(ContractDeposit.class));
    }

    @Test
    void applyReceipt_duplicateReceipt() {
        when(depositRepository.existsBySourceReceiptId(receiptId))
                .thenReturn(true);

        assertThrows(
                ContractConflictException.class,
                () -> service.applyVerifiedReceipt(contractId, receiptId)
        );

        verify(depositRepository, never())
                .save(any(ContractDeposit.class));
    }

    @Test
    void applyReceipt_unverifiedPayment() {
        when(paymentClient.verifyReceipt(receiptId))
                .thenReturn(
                        new PaymentVerificationClient.VerifiedPayment(
                                receiptId,
                                reservationId,
                                500_000L,
                                false
                        )
                );

        assertThrows(
                ContractConflictException.class,
                () -> service.applyVerifiedReceipt(contractId, receiptId)
        );

        verify(depositRepository, never())
                .save(any(ContractDeposit.class));
    }

    @Test
    void applyReceipt_wrongRoom() {
        when(reservationClient.verifyReservation(reservationId))
                .thenReturn(
                        new ReservationVerificationClient.VerifiedReservation(
                                reservationId,
                                UUID.randomUUID(),
                                userId,
                                true
                        )
                );

        assertThrows(
                ContractConflictException.class,
                () -> service.applyVerifiedReceipt(contractId, receiptId)
        );

        verify(depositRepository, never())
                .save(any(ContractDeposit.class));
    }

    @Test
    void applyReceipt_wrongUser() {
        when(reservationClient.verifyReservation(reservationId))
                .thenReturn(
                        new ReservationVerificationClient.VerifiedReservation(
                                reservationId,
                                roomId,
                                UUID.randomUUID(),
                                true
                        )
                );

        assertThrows(
                ContractConflictException.class,
                () -> service.applyVerifiedReceipt(contractId, receiptId)
        );

        verify(depositRepository, never())
                .save(any(ContractDeposit.class));
    }

    @Test
    void applyReceipt_exceedsRemainingDeposit() {
        when(depositRepository.sumAppliedAmountByContractId(contractId))
                .thenReturn(1_800_000L);

        assertThrows(
                ContractConflictException.class,
                () -> service.applyVerifiedReceipt(contractId, receiptId)
        );

        verify(depositRepository, never())
                .save(any(ContractDeposit.class));
    }
}
