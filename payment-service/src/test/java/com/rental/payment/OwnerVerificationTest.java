package com.rental.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import com.rental.payment.entity.*;
import com.rental.payment.integration.*;
import com.rental.payment.repository.*;
import com.rental.payment.service.*;

class OwnerVerificationTest {
    DepositReceiptRepository repo;
    DepositHoldVerifier verifier;
    DepositReceiptService service;
    UUID id = UUID.randomUUID(), holdId = UUID.randomUUID(), ownerId = UUID.randomUUID();
    UUID property = UUID.randomUUID(), room = UUID.randomUUID(), customer = UUID.randomUUID();
    UsernamePasswordAuthenticationToken owner;
    DepositReceipt receipt(DepositReceiptStatus status) {
        return new DepositReceipt(id, "DC-" + id, property, room, holdId, customer, ownerId,
            1000000, DepositPaymentMethod.CASH, status, null, LocalDateTime.now(), LocalDateTime.now());
    }
    @BeforeEach void setup() {
        repo = mock(DepositReceiptRepository.class); verifier = mock(DepositHoldVerifier.class);
        service = new DepositReceiptService(repo, verifier);
        owner = new UsernamePasswordAuthenticationToken(ownerId.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(repo.findById(id)).thenReturn(Optional.of(receipt(DepositReceiptStatus.REPORTED)));
        when(repo.findByHoldIdForUpdate(holdId)).thenReturn(Optional.of(receipt(DepositReceiptStatus.REPORTED)));
        when(verifier.verifyForCollection(holdId, owner)).thenReturn(new VerifiedDepositHold(holdId, property,
            room, customer, "PENDING_VERIFICATION", "RESERVED_PENDING", OffsetDateTime.now().plusHours(1), true, null));
    }
    @Test void ownerVerifiesAndPersistsIdentity() {
        when(repo.verifyOwner(id, ownerId)).thenReturn(1);
        service.verifyOwner(id, owner);
        verify(repo).verifyOwner(id, ownerId);
        verify(repo).lockHold(holdId);
    }
    @Test void staffCannotVerify() {
        var staff = new UsernamePasswordAuthenticationToken(ownerId.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.verifyOwner(id, staff)).getStatusCode().value());
        verifyNoInteractions(repo);
    }
    @Test void alreadyVerifiedIsIdempotent() {
        when(repo.findByHoldIdForUpdate(holdId)).thenReturn(Optional.of(receipt(DepositReceiptStatus.OWNER_VERIFIED)));
        assertEquals(DepositReceiptStatus.OWNER_VERIFIED, service.verifyOwner(id, owner).status());
        verify(repo, never()).verifyOwner(any(), any());
    }
    @Test void expiredHoldCannotBeVerified() {
        when(verifier.verifyForCollection(holdId, owner)).thenReturn(new VerifiedDepositHold(holdId, property,
            room, customer, "PENDING_VERIFICATION", "RESERVED_PENDING", OffsetDateTime.now().minusHours(1), false, "HOLD_EXPIRED"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.verifyOwner(id, owner)).getStatusCode().value());
        verify(repo, never()).verifyOwner(any(), any());
    }
    @Test void reportedIsNotRentalEligible() {
        var response = service.rentalEligibility(id, owner);
        assertFalse(response.verified()); assertFalse(response.eligible());
        assertEquals("RECEIPT_NOT_OWNER_VERIFIED", response.reason());
    }
    @Test void verifiedIsRentalEligible() {
        when(repo.findById(id)).thenReturn(Optional.of(receipt(DepositReceiptStatus.OWNER_VERIFIED)));
        var response = service.rentalEligibility(id, owner);
        assertTrue(response.verified()); assertEquals(1000000, response.amount());
        assertEquals(holdId, response.holdId());
    }
    @Test void propertyFailureIsNotBypassed() {
        when(verifier.verifyForCollection(holdId, owner)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.rentalEligibility(id, owner)).getStatusCode().value());
    }
}
