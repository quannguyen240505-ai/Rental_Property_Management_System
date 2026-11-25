package com.rental.rental.service;

import com.rental.rental.entity.ContractDeposit;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.ContractDepositRepository;
import com.rental.rental.repository.RentalContractRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class DepositApplicationService {

    private final ContractDepositRepository depositRepository;
    private final RentalContractRepository contractRepository;
    private final PaymentVerificationClient paymentClient;
    private final ReservationVerificationClient reservationClient;

    public DepositApplicationService(
            ContractDepositRepository depositRepository,
            RentalContractRepository contractRepository,
            PaymentVerificationClient paymentClient,
            ReservationVerificationClient reservationClient
    ) {
        this.depositRepository = depositRepository;
        this.contractRepository = contractRepository;
        this.paymentClient = paymentClient;
        this.reservationClient = reservationClient;
    }

    @Transactional
    public ContractDeposit applyVerifiedReceipt(
            UUID contractId,
            UUID receiptId
    ) {
        RentalContract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental contract not found"
                ));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new ContractConflictException(
                    "Only DRAFT contracts can receive reservation deposits"
            );
        }

        if (depositRepository.existsBySourceReceiptId(receiptId)) {
            throw new ContractConflictException(
                    "Receipt has already been applied"
            );
        }

        var payment = paymentClient.verifyReceipt(receiptId);

        if (payment == null
                || !payment.verified()
                || !receiptId.equals(payment.receiptId())
                || payment.reservationId() == null
                || payment.amount() == null
                || payment.amount() <= 0) {
            throw new ContractConflictException(
                    "Payment receipt is not valid"
            );
        }

        var reservation = reservationClient.verifyReservation(
                payment.reservationId()
        );

        if (reservation == null
                || !reservation.valid()
                || !payment.reservationId().equals(
                reservation.reservationId()
        )
                || !contract.getRoomId().equals(
                reservation.roomId()
        )
                || contract.getTenantProfile().getUserId() == null
                || !contract.getTenantProfile().getUserId().equals(
                reservation.userId()
        )) {
            throw new ContractConflictException(
                    "Reservation does not match rental contract"
            );
        }

        Long applied = depositRepository
                .sumAppliedAmountByContractId(contractId);

        if (applied == null) {
            applied = 0L;
        }

        long required = contract.getDepositRequired();

        if (applied < 0 || applied > required) {
            throw new ContractConflictException(
                    "Invalid contract deposit balance"
            );
        }

        long remaining = required - applied;

        if (payment.amount() > remaining) {
            throw new ContractConflictException(
                    "Payment exceeds remaining deposit"
            );
        }

        ContractDeposit deposit = new ContractDeposit();
        deposit.setContract(contract);
        deposit.setSourceReceiptId(receiptId);
        deposit.setAppliedAmount(payment.amount());

        return depositRepository.save(deposit);
    }
}
