package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.entity.*;
import com.rental.property.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomHoldServiceTest {
    private final RoomHoldRepository holds = mock(RoomHoldRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ViewingAppointmentRepository viewings = mock(ViewingAppointmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final UUID roomId = UUID.randomUUID();
    private final UUID viewingId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final Property property = mock(Property.class);
    private final Room room = mock(Room.class);
    private final ViewingAppointment viewing = mock(ViewingAppointment.class);
    private RoomHoldService service;

    @BeforeEach
    void setup() {
        reset(holds, rooms, viewings, property, room, viewing);
        service = new RoomHoldService(holds, rooms, viewings, clock);
        when(room.getId()).thenReturn(roomId);
        when(room.getProperty()).thenReturn(property);
        when(property.getId()).thenReturn(UUID.randomUUID());
        when(viewing.getId()).thenReturn(viewingId);
        when(viewing.getRoom()).thenReturn(room);
        when(viewing.getCustomerId()).thenReturn(customerId);
        when(viewing.getStaffId()).thenReturn(staffId);
        when(viewing.getStatus()).thenReturn(ViewingStatus.COMPLETED);
        when(viewings.findById(viewingId)).thenReturn(Optional.of(viewing));
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(RoomStatus.AVAILABLE);
        when(holds.saveAndFlush(any(RoomHold.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LocalDateTime inHour() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone()).plusHours(1);
    }

    @Test void viewingPropertyLookupUsesTheRoomInsideServiceBoundary() {
        UUID propertyId = UUID.randomUUID();
        when(property.getId()).thenReturn(propertyId);
        assertEquals(propertyId, service.viewingPropertyId(viewingId));
        verify(viewings).findById(viewingId);
        verify(viewing).getRoom();
    }

    @Test void nonexistentViewingPropertyLookupReturns404() {
        UUID unknownId=UUID.randomUUID();
        when(viewings.findById(unknownId)).thenReturn(Optional.empty());
        status(404,()->service.viewingPropertyId(unknownId));
    }

    private CreateRoomHoldRequest request() {
        return new CreateRoomHoldRequest(viewingId, "QA-RECEIPT-A", inHour());
    }

    private void status(int expected, Runnable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, ex.getStatusCode().value());
    }

    @Test void staffCreatesPendingFromCompletedViewing() {
        var result = service.create(request(), staffId, true);
        assertEquals(RoomHoldStatus.PENDING_VERIFICATION, result.status());
        assertEquals(customerId, result.customerId());
        verify(room).setStatus(RoomStatus.RESERVED_PENDING);
        verify(rooms).saveAndFlush(room);
    }

    @Test void cannotCreateWithPastExpiry() {
        var request = new CreateRoomHoldRequest(viewingId,"RECEIPT",inHour().minusHours(2));
        status(400, () -> service.create(request,staffId,true));
        verifyNoInteractions(holds);
    }

    @Test void cannotCreateHoldForUncompletedViewing() {
        when(viewing.getStatus()).thenReturn(ViewingStatus.REQUESTED);
        status(409, () -> service.create(request(),staffId,true));
        verify(rooms,never()).saveAndFlush(any(Room.class));
    }

    @Test void cannotCreateForReservedRoom() {
        when(room.getStatus()).thenReturn(RoomStatus.RESERVED);
        status(409, () -> service.create(request(),staffId,true));
    }

    @Test void unrelatedStaffCannotCreateHold() {
        status(403, () -> service.create(request(),UUID.randomUUID(),true));
        verify(rooms,never()).findForUpdateById(any(UUID.class));
    }

    @Test void ownerCanCreateHold() {
        assertEquals(RoomHoldStatus.PENDING_VERIFICATION,
                service.create(request(),ownerId,false).status());
    }


@Test void createWithoutReceiptIsAllowedBeforePayment() {
    var noReceipt = new CreateRoomHoldRequest(viewingId, null, inHour());
    var result = service.create(noReceipt, staffId, true);
    assertEquals(RoomHoldStatus.PENDING_VERIFICATION, result.status());
    verify(holds, never()).existsByReceiptReference(any());
    verify(room).setStatus(RoomStatus.RESERVED_PENDING);
}

@Test void emptyLegacyReceiptStillRejected() {
    var blankReceipt = new CreateRoomHoldRequest(viewingId, "  ", inHour());
    status(400, () -> service.create(blankReceipt, staffId, true));
}

private RoomHold mockEligibilityHold(UUID holdId, RoomHoldStatus status, LocalDateTime expiry) {
    RoomHold hold = mock(RoomHold.class);
    when(holds.findById(holdId)).thenReturn(Optional.of(hold));
    when(hold.getId()).thenReturn(holdId);
    when(hold.getRoom()).thenReturn(room);
    when(hold.getStatus()).thenReturn(status);
    when(hold.getCustomerId()).thenReturn(customerId);
    when(hold.getExpiresAt()).thenReturn(expiry);
    return hold;
}

@Test void depositEligibilityTrueOnlyForPendingUnexpiredReservedRoom() {
    UUID holdId = UUID.randomUUID();
    mockEligibilityHold(holdId, RoomHoldStatus.PENDING_VERIFICATION, inHour());
    when(room.getStatus()).thenReturn(RoomStatus.RESERVED_PENDING);
    var response = service.depositEligibility(holdId);
    assertTrue(response.eligible());
    assertNull(response.reason());
    assertEquals(ZoneOffset.ofHours(7), response.expiresAt().getOffset());
    assertEquals(inHour(), response.expiresAt().toLocalDateTime());
    assertEquals(customerId, response.customerId());
    assertEquals(roomId, response.roomId());
}

@Test void depositEligibilityFalseForExpiredHold() {
    UUID holdId = UUID.randomUUID();
    mockEligibilityHold(holdId, RoomHoldStatus.PENDING_VERIFICATION, inHour().minusHours(2));
    when(room.getStatus()).thenReturn(RoomStatus.RESERVED_PENDING);
    var response = service.depositEligibility(holdId);
    assertFalse(response.eligible());
    assertEquals("HOLD_EXPIRED", response.reason());
}

@Test void depositEligibilityFalseForApprovedHold() {
    UUID holdId = UUID.randomUUID();
    mockEligibilityHold(holdId, RoomHoldStatus.APPROVED, inHour());
    when(room.getStatus()).thenReturn(RoomStatus.RESERVED);
    var response = service.depositEligibility(holdId);
    assertFalse(response.eligible());
    assertEquals("INVALID_HOLD_STATUS", response.reason());
}

    @Test void depositEligibilityFalseForWrongRoomStatus() {
        UUID holdId = UUID.randomUUID();
        mockEligibilityHold(holdId, RoomHoldStatus.PENDING_VERIFICATION, inHour());
        when(room.getStatus()).thenReturn(RoomStatus.AVAILABLE);
        var response = service.depositEligibility(holdId);
        assertFalse(response.eligible());
        assertEquals("INVALID_ROOM_STATUS", response.reason());
    }

    @Test void legacyReceiptReferenceMustNotBeInterpretedAsPaymentReceipt() {
        UUID holdId = UUID.randomUUID();
        RoomHold hold = mockEligibilityHold(holdId,
                RoomHoldStatus.PENDING_VERIFICATION, inHour());
        when(room.getStatus()).thenReturn(RoomStatus.RESERVED_PENDING);
        assertTrue(service.depositEligibility(holdId).eligible());
        verify(hold, never()).getReceiptReference();
    }

    @Test void depositEligibilityUnknownHoldReturns404() {
        UUID unknownId=UUID.randomUUID();
        when(holds.findById(unknownId)).thenReturn(Optional.empty());
        status(404,()->service.depositEligibility(unknownId));
    }

    @Test void duplicateReceiptIsRejected() {
        when(holds.existsByReceiptReference("QA-RECEIPT-A")).thenReturn(true);
        status(409, () -> service.create(request(),staffId,true));
    }

    @Test void ownerApprovesOnlyPendingNonExpiredHold() {
        UUID id=UUID.randomUUID();
        RoomHold h=mock(RoomHold.class);
        when(h.getRoom()).thenReturn(room);
        when(h.getViewing()).thenReturn(viewing);
        when(h.getStatus()).thenReturn(RoomHoldStatus.PENDING_VERIFICATION);
        when(h.getExpiresAt()).thenReturn(inHour());
        when(holds.findRoomIdById(id)).thenReturn(Optional.of(roomId));
        when(holds.findForUpdate(id)).thenReturn(Optional.of(h));
        when(room.getStatus()).thenReturn(RoomStatus.RESERVED_PENDING);
        service.approve(id,ownerId,"OWNER-VERIFIED-RECEIPT");
        verify(h).approve(eq(ownerId),eq("OWNER-VERIFIED-RECEIPT"),any(LocalDateTime.class));
        verify(room).setStatus(RoomStatus.RESERVED);
    }

    @Test void cannotApproveExpiredHold() {
        UUID id=UUID.randomUUID();
        RoomHold h=mock(RoomHold.class);
        when(h.getRoom()).thenReturn(room);
        when(h.getStatus()).thenReturn(RoomHoldStatus.PENDING_VERIFICATION);
        when(h.getExpiresAt()).thenReturn(inHour().minusHours(2));
        when(holds.findRoomIdById(id)).thenReturn(Optional.of(roomId));
        when(holds.findForUpdate(id)).thenReturn(Optional.of(h));
        when(room.getStatus()).thenReturn(RoomStatus.RESERVED_PENDING);
        status(409, () -> service.approve(id,ownerId,"VERIFIED"));
        verify(room,never()).setStatus(RoomStatus.RESERVED);
    }

    @Test void cannotCancelWithoutDepositResolutionReference() {
        status(400, () -> service.cancel(UUID.randomUUID(),ownerId," "));
    }

    @Test void cannotExpireBeforeDeadline() {
        UUID id=UUID.randomUUID();
        RoomHold h=mock(RoomHold.class);
        when(h.getRoom()).thenReturn(room);
        when(h.getStatus()).thenReturn(RoomHoldStatus.PENDING_VERIFICATION);
        when(h.getExpiresAt()).thenReturn(inHour());
        when(holds.findRoomIdById(id)).thenReturn(Optional.of(roomId));
        when(holds.findForUpdate(id)).thenReturn(Optional.of(h));
        status(409, () -> service.expire(id,ownerId,"REFUND-SETTLED"));
    }
}
