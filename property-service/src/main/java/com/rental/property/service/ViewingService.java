package com.rental.property.service;

import com.rental.property.dto.request.CreateViewingRequest;
import com.rental.property.dto.response.ViewingResponse;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import com.rental.property.entity.ViewingAppointment;
import com.rental.property.entity.ViewingStatus;
import com.rental.property.repository.RoomRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import com.rental.property.repository.ViewingAppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ViewingService {
    private static final List<ViewingStatus> OPEN =
            List.of(ViewingStatus.REQUESTED, ViewingStatus.CONFIRMED);
    private final ViewingAppointmentRepository viewings;
    private final RoomRepository rooms;
    private final StaffPropertyAssignmentRepository assignments;
    private final Clock clock;

    @Autowired
    public ViewingService(ViewingAppointmentRepository viewings, RoomRepository rooms,
                          StaffPropertyAssignmentRepository assignments) {
        this(viewings, rooms, assignments, Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    // Package-private constructor for deterministic unit tests.
    ViewingService(ViewingAppointmentRepository viewings, RoomRepository rooms,
                   StaffPropertyAssignmentRepository assignments, Clock clock) {
        this.viewings = viewings;
        this.rooms = rooms;
        this.assignments = assignments;
        this.clock = clock;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private Room room(UUID id) {
        return rooms.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    private ViewingAppointment getEntity(UUID id) {
        return viewings.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
    }

    private ViewingResponse map(ViewingAppointment v) {
        return new ViewingResponse(v.getId(), v.getRoom().getId(),
                v.getRoom().getProperty().getId(), v.getCustomerId(), v.getStaffId(),
                v.getStatus(), v.getStartAt(), v.getEndAt(), v.getResultNotes(),
                v.getCreatedAt(), v.getUpdatedAt());
    }

    @Transactional
    public ViewingResponse create(CreateViewingRequest req, UUID customerId) {
        if (req == null || req.roomId() == null || req.startAt() == null || req.endAt() == null
                || !req.endAt().isAfter(req.startAt()) || !req.startAt().isAfter(now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid future viewing time");
        }
        Room r = room(req.roomId());
        if (!Boolean.TRUE.equals(r.getPublished()) || r.getStatus() != RoomStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room not available for viewing");
        }
        if (viewings.countCustomerOverlap(customerId, r.getId(), OPEN,
                req.startAt(), req.endAt()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate active viewing request");
        }
        ViewingAppointment saved = viewings.saveAndFlush(
                new ViewingAppointment(r, customerId, req.startAt(), req.endAt()));
        // A viewing NEVER transitions the room to RESERVED_PENDING/RESERVED.
        return map(saved);
    }

    @Transactional(readOnly = true)
    public ViewingResponse get(UUID id) { return map(getEntity(id)); }

    @Transactional(readOnly = true)
    public List<ViewingResponse> mine(UUID customerId) {
        return viewings.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<ViewingResponse> available(boolean owner, List<UUID> assignedPropertyIds) {
        List<ViewingAppointment> result = owner
                ? viewings.findByStatusOrderByStartAtAsc(ViewingStatus.REQUESTED)
                : assignedPropertyIds.isEmpty() ? List.of()
                : viewings.findByStatusAndRoom_Property_IdInOrderByStartAtAsc(
                        ViewingStatus.REQUESTED, assignedPropertyIds);
        return result.stream().map(this::map).toList();
    }

    /** Lock order: staff's assignment row(s) -> room row -> viewing row.
     * This serializes claims for the same STAFF and for the same room.
     * The database (not a JVM synchronized block) arbitrates concurrent requests.
     */
    @Transactional
    public ViewingResponse claim(UUID id, UUID actorId, boolean staffActor) {
        if (staffActor && assignments.lockByStaffId(actorId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No assigned property");
        }
        ViewingAppointment snapshot = getEntity(id);
        UUID roomId = snapshot.getRoom().getId();
        Room lockedRoom = rooms.findForUpdateById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        ViewingAppointment v = viewings.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
        if (v.getStatus() != ViewingStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Viewing already claimed or closed");
        }
        if (!v.getStartAt().isAfter(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Viewing time already passed");
        }
        if (!Boolean.TRUE.equals(lockedRoom.getPublished())
                || lockedRoom.getStatus() != RoomStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room no longer available");
        }
        if (viewings.countRoomOverlap(roomId, id, ViewingStatus.CONFIRMED,
                v.getStartAt(), v.getEndAt()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room viewing time conflict");
        }
        if (viewings.countStaffOverlap(actorId, id, ViewingStatus.CONFIRMED,
                v.getStartAt(), v.getEndAt()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff already has another viewing");
        }
        v.claim(actorId);
        return map(viewings.saveAndFlush(v));
    }

    @Transactional
    public ViewingResponse finish(UUID id, UUID actorId, boolean owner,
                                  ViewingStatus result, String notes) {
        ViewingAppointment v = viewings.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
        if (v.getStatus() != ViewingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CONFIRMED viewings can be completed");
        }
        if (!owner && !actorId.equals(v.getStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only assigned staff may update result");
        }
        if (result != ViewingStatus.COMPLETED && result != ViewingStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Result must be COMPLETED or NO_SHOW");
        }
        if (notes != null && notes.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes too long");
        }
        v.finish(result, notes, actorId);
        return map(viewings.saveAndFlush(v));
    }

    @Transactional
    public ViewingResponse cancel(UUID id, UUID customerId) {
        ViewingAppointment v = viewings.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing not found"));
        if (!customerId.equals(v.getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot cancel another user's viewing");
        }
        if (!OPEN.contains(v.getStatus()) || !v.getStartAt().isAfter(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Viewing cannot be canceled at this stage");
        }
        v.cancel(customerId);
        return map(viewings.saveAndFlush(v));
    }
}
