package com.rental.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.rental.payment.dto.request.CreateDepositReceiptRequest;
import com.rental.payment.entity.DepositPaymentMethod;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.entity.DepositReceiptStatus;
import com.rental.payment.integration.DepositHoldVerifier;
import com.rental.payment.integration.VerifiedDepositHold;
import com.rental.payment.repository.DepositReceiptRepository;
import com.rental.payment.service.DepositReceiptService;

class DepositReceiptServiceTest {

    private DepositReceiptRepository repository;
    private DepositHoldVerifier verifier;
    private DepositReceiptService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(DepositReceiptRepository.class);
        verifier = mock(DepositHoldVerifier.class);
        service = new DepositReceiptService(repository, verifier);
    }

    @Test
    void createsReportedReceiptUsingVerifiedHoldAndLoggedInCollector() {
        Authentication staff = authentication(
                userId.toString(), "ROLE_STAFF");

        var hold = new VerifiedDepositHold(
                holdId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(), "PENDING_VERIFICATION", "RESERVED_PENDING",
                java.time.OffsetDateTime.now().plusHours(1), true, null);

        when(verifier.verifyForCollection(holdId, staff))
                .thenReturn(hold);

        var response = service.create(request(500000L), staff);

        var captor = ArgumentCaptor.forClass(DepositReceipt.class);
        verify(repository).insert(captor.capture());
        var saved = captor.getValue();

        assertAll(
                () -> assertEquals(hold.propertyId(), saved.propertyId()),
                () -> assertEquals(hold.roomId(), saved.roomId()),
                () -> assertEquals(hold.customerId(), saved.customerId()),
                () -> assertEquals(holdId, saved.holdId()),
                () -> assertEquals(userId, saved.collectedBy()),
                () -> assertEquals(500000L, saved.amount()),
                () -> assertEquals(
                        DepositReceiptStatus.REPORTED, saved.status()),
                () -> assertEquals(saved.id(), response.id()),
                () -> assertEquals(
                        "DC-" + saved.id(), saved.receiptCode()));
    }

    @Test
    void rejectsCustomerWithoutCallingVerifierOrSaving() {
        var customer = authentication(
                userId.toString(), "ROLE_CUSTOMER");

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.create(request(500000L), customer));

        verifyNoInteractions(verifier, repository);
    }

    @Test
    void rejectsMissingAuthenticationWithoutSaving() {
        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> service.create(request(500000L), null));

        verifyNoInteractions(verifier, repository);
    }

    @Test
    void rejectsInvalidSubjectWithoutSaving() {
        var staff = authentication("invalid-user-id", "ROLE_STAFF");

        assertStatus(HttpStatus.UNAUTHORIZED,
                () -> service.create(request(500000L), staff));

        verifyNoInteractions(verifier, repository);
    }

    @Test
    void rejectsZeroAmountBeforeVerifyingHold() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.create(request(0L), staff));

        verifyNoInteractions(verifier, repository);
    }

    @Test
    void doesNotSaveWhenHoldVerificationIsUnavailable() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");

        when(verifier.verifyForCollection(holdId, staff))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                () -> service.create(request(500000L), staff));

        verify(repository, never()).insert(any());
    }

    @Test
    void doesNotSaveWhenVerifierReturnsDifferentHold() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");

        when(verifier.verifyForCollection(holdId, staff))
                .thenReturn(new VerifiedDepositHold(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(), "PENDING_VERIFICATION", "RESERVED_PENDING",
                        java.time.OffsetDateTime.now().plusHours(1), true, null));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                () -> service.create(request(500000L), staff));

        verify(repository, never()).insert(any());
    }


    @Test
    void rejectsExpiredHoldEvenWhenPropertySaysEligible() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), h.holdStatus(), h.roomStatus(),
                java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(7)).minusSeconds(1), true, null));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsIneligibleHold() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), h.holdStatus(), h.roomStatus(),
                h.expiresAt(), false, "HOLD_EXPIRED"));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsApprovedHoldDespiteEligibleFlag() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), "APPROVED", h.roomStatus(),
                h.expiresAt(), true, null));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsWrongRoomStatusDespiteEligibleFlag() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), h.holdStatus(), "AVAILABLE",
                h.expiresAt(), true, null));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
    }

    @Test
    void identicalRetryReturnsOriginalEvenWhenPropertyReportsReceiptExists() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), h.propertyId(), h.roomId(), h.customerId(), h.holdStatus(), h.roomStatus(),
                h.expiresAt(), false, "RECEIPT_ALREADY_EXISTS"));
        var receipt = existingReceipt(h);
        when(repository.findByHoldIdForUpdate(holdId)).thenReturn(java.util.Optional.of(receipt));
        assertEquals(receipt.id(), service.create(request(500000L), staff).id());
        verify(repository, never()).insert(any());
        verify(verifier).verifyForCollection(holdId, staff);
    }

    @Test
    void changedRetryAmountReturns409() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(h);
        when(repository.findByHoldIdForUpdate(holdId)).thenReturn(java.util.Optional.of(existingReceipt(h)));
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(600000L), staff));
        verify(repository, never()).insert(any());
    }

    @Test
    void retryAfterStaffAssignmentRevokedReturns403() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        when(verifier.verifyForCollection(holdId, staff))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
        verify(repository, never()).findByHoldIdForUpdate(any());
    }

    @Test
    void uniqueConstraintFailureReturns409AndEscapesTransaction() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(validHold());
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
                .when(repository).insert(any());
        assertStatus(HttpStatus.CONFLICT, () -> service.create(request(500000L), staff));
    }

    @Test
    void rejectsIncompletePropertyIdentityBeforeInsert() {
        var staff = authentication(userId.toString(), "ROLE_STAFF");
        var h = validHold();
        when(verifier.verifyForCollection(holdId, staff)).thenReturn(new VerifiedDepositHold(
                h.holdId(), null, h.roomId(), h.customerId(), h.holdStatus(), h.roomStatus(),
                h.expiresAt(), true, null));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.create(request(500000L), staff));
        verify(repository, never()).insert(any());
    }

    private VerifiedDepositHold validHold() {
        return new VerifiedDepositHold(holdId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PENDING_VERIFICATION", "RESERVED_PENDING", java.time.OffsetDateTime.now().plusHours(1), true, null);
    }

    private DepositReceipt existingReceipt(VerifiedDepositHold h) {
        var id = UUID.randomUUID();
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        return new DepositReceipt(id, "DC-" + id, h.propertyId(), h.roomId(), h.holdId(), h.customerId(),
                userId, 500000L, DepositPaymentMethod.CASH, DepositReceiptStatus.REPORTED, null, now, now);
    }

    private CreateDepositReceiptRequest request(Long amount) {
        return new CreateDepositReceiptRequest(
                holdId, amount, DepositPaymentMethod.CASH, null);
    }

    private Authentication authentication(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject,
                null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        var exception = assertThrows(
                ResponseStatusException.class, action::run);

        assertEquals(expected, exception.getStatusCode());
    }
}
