package com.rental.payment.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rental.payment.dto.request.CreateDepositReceiptRequest;
import com.rental.payment.dto.response.DepositReceiptResponse;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.entity.DepositReceiptStatus;
import com.rental.payment.integration.DepositHoldVerifier;
import com.rental.payment.integration.VerifiedDepositHold;
import com.rental.payment.repository.DepositReceiptRepository;

@Service
public class DepositReceiptService {
    private final DepositReceiptRepository repository;
    private final DepositHoldVerifier holdVerifier;

    public DepositReceiptService(DepositReceiptRepository repository, DepositHoldVerifier holdVerifier) {
        this.repository = repository;
        this.holdVerifier = holdVerifier;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DepositReceiptResponse create(CreateDepositReceiptRequest request, Authentication authentication) {
        UUID collector = requireCollector(authentication);
        if (request == null || request.holdId() == null || request.amount() == null
                || request.amount() <= 0 || request.paymentMethod() == null
                || (request.evidenceUrl() != null && request.evidenceUrl().length() > 500)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_RECEIPT_REQUEST");
        }

        // All instances serialize on the same database row, held until commit/rollback.
        repository.lockHold(request.holdId());
        // Always recheck current Property permissions, including on an idempotent replay.
        var hold = holdVerifier.verifyForCollection(request.holdId(), authentication);
        validateResponse(request.holdId(), hold);
        var existing = repository.findByHoldIdForUpdate(request.holdId());
        if (existing.isPresent()) {
            DepositReceipt receipt = existing.get();
            boolean sameIdentity = receipt.propertyId().equals(hold.propertyId())
                    && receipt.roomId().equals(hold.roomId()) && receipt.customerId().equals(hold.customerId());
            boolean sameRequest = receipt.amount() == request.amount()
                    && receipt.paymentMethod() == request.paymentMethod()
                    && Objects.equals(receipt.evidenceUrl(), request.evidenceUrl())
                    && receipt.collectedBy().equals(collector);
            if (!sameIdentity || !sameRequest || receipt.status() == DepositReceiptStatus.CANCELLED) {
                throw error(HttpStatus.CONFLICT, "RECEIPT_ALREADY_EXISTS");
            }
            // Returning the original is not a new collection: expiry/approval may have changed.
            return DepositReceiptResponse.from(receipt);
        }

        if (!hold.eligible()) {
            String reason = Set.of("HOLD_EXPIRED", "INVALID_HOLD_STATUS", "INVALID_ROOM_STATUS", "RECEIPT_ALREADY_EXISTS")
                    .contains(hold.reason()) ? hold.reason() : "HOLD_NOT_ELIGIBLE";
            throw error(HttpStatus.CONFLICT, reason);
        }
        if (!"PENDING_VERIFICATION".equals(hold.holdStatus())) {
            throw error(HttpStatus.CONFLICT, "INVALID_HOLD_STATUS");
        }
        if (!"RESERVED_PENDING".equals(hold.roomStatus())) {
            throw error(HttpStatus.CONFLICT, "INVALID_ROOM_STATUS");
        }
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var receipt = new DepositReceipt(id, "DC-" + id, hold.propertyId(), hold.roomId(), hold.holdId(),
                hold.customerId(), collector, request.amount(), request.paymentMethod(),
                DepositReceiptStatus.REPORTED, request.evidenceUrl(), now, now);
        // Check the offset-aware expiry again immediately before the local write.
        if (!Instant.now().isBefore(hold.expiresAt().toInstant())) {
            throw error(HttpStatus.CONFLICT, "HOLD_EXPIRED");
        }
        try {
            repository.insert(receipt);
        } catch (DuplicateKeyException ex) {
            // Throw out of the transaction so Spring rolls it back completely.
            throw error(HttpStatus.CONFLICT, "RECEIPT_ALREADY_EXISTS");
        }
        return DepositReceiptResponse.from(receipt);
    }

    private void validateResponse(UUID holdId, VerifiedDepositHold hold) {
        if (hold == null || !holdId.equals(hold.holdId()) || hold.propertyId() == null
                || hold.roomId() == null || hold.customerId() == null || hold.eligible() == null
                || hold.holdStatus() == null || hold.roomStatus() == null || hold.expiresAt() == null
                || (!hold.eligible() && (hold.reason() == null || hold.reason().isBlank()))) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "PROPERTY_SERVICE_UNAVAILABLE");
        }
    }

    private UUID requireCollector(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (authentication.getAuthorities().stream().noneMatch(a ->
                "ROLE_STAFF".equals(a.getAuthority()) || "ROLE_OWNER".equals(a.getAuthority()))) {
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        try {
            UUID id = UUID.fromString(authentication.getName());
            if (!id.toString().equalsIgnoreCase(authentication.getName())) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException ex) {
            throw error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
    }

    private ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
