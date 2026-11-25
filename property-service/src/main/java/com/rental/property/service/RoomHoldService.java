package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.dto.response.RoomHoldResponse;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomHold;
import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import com.rental.property.entity.ViewingAppointment;
import com.rental.property.entity.ViewingStatus;
import com.rental.property.repository.RoomHoldRepository;
import com.rental.property.repository.RoomRepository;
import com.rental.property.repository.ViewingAppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class RoomHoldService {
    private static final Duration MAX_DURATION = Duration.ofHours(72);
    private static final Duration MIN_DURATION = Duration.ofMinutes(5);
    private final RoomHoldRepository holds;
    private final RoomRepository rooms;
    private final ViewingAppointmentRepository viewings;
    private final Clock clock;

    @Autowired
    public RoomHoldService(RoomHoldRepository holds, RoomRepository rooms,
                           ViewingAppointmentRepository viewings) {
        this(holds, rooms, viewings, Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    // Package-private for deterministic tests.
    RoomHoldService(RoomHoldRepository holds, RoomRepository rooms,
                    ViewingAppointmentRepository viewings, Clock clock) {
        this.holds = holds;
        this.rooms = rooms;
        this.viewings = viewings;
        this.clock = clock;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private String reference(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " required (max 100 chars)");
        }
        return value.trim();
    }

    /**
     * Resolve the viewing's property inside a read-only persistence transaction.
     * The Controller must not traverse a LAZY room association after the
     * repository call completes when spring.jpa.open-in-view=false.
     */
    @Transactional(readOnly = true)
    public UUID viewingPropertyId(UUID viewingId) {
        if (viewingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewingId required");
        }
        ViewingAppointment viewing = viewings.findById(viewingId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
        return viewing.getRoom().getProperty().getId();
    }

    private RoomHold getEntity(UUID id) {
        return holds.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room hold not found"));
    }

    private Room lockRoom(UUID roomId) {
        return rooms.findForUpdateById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    private RoomHold lockHold(UUID holdId) {
        return holds.findForUpdate(holdId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room hold not found"));
    }

    private RoomHoldResponse map(RoomHold h) {
        Room r = h.getRoom();
        return new RoomHoldResponse(h.getId(), h.getViewing().getId(), r.getId(),
                r.getProperty().getId(), h.getCustomerId(), h.getCreatedBy(),
                h.getReceiptReference(), h.getExpiresAt(), h.getStatus(), r.getStatus(),
                h.getApprovedBy(), h.getApprovedAt(), h.getVerificationReference(),
                h.getClosedBy(), h.getClosedAt(), h.getDepositResolutionReference(),
                h.getCreatedAt(), h.getUpdatedAt());
    }

    // A hold MUST be derived from a COMPLETED viewing; client cannot inject customerId.
    // Locking the room serializes concurrent requests from different viewings.
    @Transactional
    public RoomHoldResponse create(CreateRoomHoldRequest req, UUID actorId, boolean staffActor) {
        if (req == null || req.viewingId() == null || req.expiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewingId and expiresAt required");
        }
        String receipt = reference(req.receiptReference(), "receiptReference");
        LocalDateTime current = now();
        Duration duration = Duration.between(current, req.expiresAt());
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hold lifetime must be 5 min to 72 hours");
        }
        ViewingAppointment v = viewings.findById(req.viewingId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
        // STAFF who recorded a successful viewing must be the one creating the hold.
        if (staffActor && !actorId.equals(v.getStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the viewing's STAFF may create hold");
        }
        Room locked = lockRoom(v.getRoom().getId());
        if (v.getStatus() != ViewingStatus.COMPLETED
                || locked.getStatus() != RoomStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Viewing must be completed and room AVAILABLE");
        }
        if (holds.existsByViewing_Id(v.getId()) || holds.existsByReceiptReference(receipt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate viewing or receipt reference");
        }
        RoomHold hold = new RoomHold(locked, v, actorId, receipt, req.expiresAt());
        locked.setStatus(RoomStatus.RESERVED_PENDING);
        try {
            RoomHold saved = holds.saveAndFlush(hold);
            rooms.saveAndFlush(locked);
            return map(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate hold", ex);
        }
    }

    // Manual OWNER attestation; no claim of automatic payment verification.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomHoldResponse approve(UUID id, UUID ownerId, String verificationReference) {
        String proof = reference(verificationReference, "verificationReference");
        // Projection first: avoid caching a stale @Version entity while waiting.
        UUID roomId = holds.findRoomIdById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room hold not found"));
        Room room = lockRoom(roomId);
        RoomHold hold = lockHold(id);
        if (hold.getStatus() != RoomHoldStatus.PENDING_VERIFICATION
                || room.getStatus() != RoomStatus.RESERVED_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold not pending");
        }
        LocalDateTime current = now();
        if (!current.isBefore(hold.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold expired");
        }
        hold.approve(ownerId, proof, current);
        room.setStatus(RoomStatus.RESERVED);
        holds.saveAndFlush(hold);
        rooms.saveAndFlush(room);
        return map(hold);
    }

    // The resolution reference documents how the deposit was handled BEFORE reopening.
    private RoomHoldResponse close(UUID id, UUID ownerId, String depositResolutionReference,
                                   RoomHoldStatus outcome) {
        String resolution = reference(depositResolutionReference, "depositResolutionReference");
        // Projection first: avoid caching a stale @Version entity while waiting.
        UUID roomId = holds.findRoomIdById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room hold not found"));
        Room room = lockRoom(roomId);
        RoomHold hold = lockHold(id);
        if (hold.getStatus() != RoomHoldStatus.PENDING_VERIFICATION
                && hold.getStatus() != RoomHoldStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold already finalized");
        }
        if (outcome == RoomHoldStatus.EXPIRED && now().isBefore(hold.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold not expired yet");
        }
        RoomStatus expected = hold.getStatus() == RoomHoldStatus.APPROVED
                ? RoomStatus.RESERVED : RoomStatus.RESERVED_PENDING;
        if (room.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room status has changed");
        }
        hold.close(outcome, ownerId, resolution, now());
        room.setStatus(RoomStatus.AVAILABLE);
        holds.saveAndFlush(hold);
        rooms.saveAndFlush(room);
        return map(hold);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomHoldResponse cancel(UUID id, UUID ownerId, String resolution) {
        return close(id, ownerId, resolution, RoomHoldStatus.CANCELLED);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomHoldResponse expire(UUID id, UUID ownerId, String resolution) {
        return close(id, ownerId, resolution, RoomHoldStatus.EXPIRED);
    }

    @Transactional(readOnly = true)
    public RoomHoldResponse get(UUID id) { return map(getEntity(id)); }

    @Transactional(readOnly = true)
    public List<RoomHoldResponse> byProperty(UUID propertyId) {
        return holds.findByRoom_Property_IdOrderByCreatedAtDesc(propertyId)
                .stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomHoldResponse> mine(UUID customerId) {
        return holds.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::map).toList();
    }
}
