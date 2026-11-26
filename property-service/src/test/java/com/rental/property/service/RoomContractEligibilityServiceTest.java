package com.rental.property.service;

import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomHold;
import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import com.rental.property.repository.RoomHoldRepository;
import com.rental.property.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomContractEligibilityServiceTest {
    private final UUID roomId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID holdId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private RoomRepository roomRepository;
    private RoomHoldRepository holdRepository;
    private RoomContractEligibilityService service;
    private Room room;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        holdRepository = mock(RoomHoldRepository.class);
        service = new RoomContractEligibilityService(roomRepository, holdRepository);
        Property property = mock(Property.class);
        when(property.getId()).thenReturn(propertyId);
        room = new Room();
        room.setProperty(property);
        room.setStatus(RoomStatus.AVAILABLE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(holdRepository.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED))
                .thenReturn(List.of());
    }

    @Test
    void availableWithoutApprovedHold_allowsDraftWithoutHold() {
        var result = service.check(roomId);
        assertTrue(result.eligible());
        assertEquals(roomId, result.roomId());
        assertEquals(propertyId, result.propertyId());
        assertEquals(RoomStatus.AVAILABLE, result.roomStatus());
        assertNull(result.reason());
        assertFalse(result.requiresHoldId());
        assertNull(result.holdId());
        assertNull(result.holdStatus());
        assertNull(result.customerId());
    }

    @Test
    void availableWithApprovedHold_failsClosed() {
        when(holdRepository.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED))
                .thenAnswer(invocation -> List.of(approvedHold()));
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertEquals("ROOM_HOLD_INCONSISTENT", result.reason());
    }

    @Test
    void reservedWithOneApprovedHold_exposesMatchingCustomerAndHold() {
        room.setStatus(RoomStatus.RESERVED);
        when(holdRepository.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED))
                .thenAnswer(invocation -> List.of(approvedHold()));
        var result = service.check(roomId);
        assertTrue(result.eligible());
        assertTrue(result.requiresHoldId());
        assertEquals(holdId, result.holdId());
        assertEquals(RoomHoldStatus.APPROVED, result.holdStatus());
        assertEquals(customerId, result.customerId());
        assertNull(result.reason());
    }

    @Test
    void reservedWithoutApprovedHold_isIneligible() {
        room.setStatus(RoomStatus.RESERVED);
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertTrue(result.requiresHoldId());
        assertEquals("APPROVED_HOLD_NOT_FOUND", result.reason());
        assertNull(result.holdId());
    }

    @Test
    void reservedWithMultipleApprovedHolds_failsClosed() {
        room.setStatus(RoomStatus.RESERVED);
        when(holdRepository.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED))
                .thenAnswer(invocation -> List.of(approvedHold(), approvedHold()));
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertEquals("MULTIPLE_APPROVED_HOLDS", result.reason());
    }

    @Test
    void reservedWithIncompleteApprovedHold_isIneligible() {
        room.setStatus(RoomStatus.RESERVED);
        RoomHold incomplete = mock(RoomHold.class);
        when(incomplete.getId()).thenReturn(holdId);
        when(incomplete.getCustomerId()).thenReturn(customerId);
        when(incomplete.getStatus()).thenReturn(RoomHoldStatus.APPROVED);
        when(holdRepository.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED))
                .thenReturn(List.of(incomplete));
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertEquals("APPROVED_HOLD_INCOMPLETE", result.reason());
    }

    @Test
    void reservedPending_isIneligible() {
        room.setStatus(RoomStatus.RESERVED_PENDING);
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertEquals("ROOM_PENDING_VERIFICATION", result.reason());
        verifyNoInteractions(holdRepository);
    }

    @Test
    void occupied_isIneligible() {
        room.setStatus(RoomStatus.OCCUPIED);
        var result = service.check(roomId);
        assertFalse(result.eligible());
        assertEquals("ROOM_OCCUPIED", result.reason());
        verifyNoInteractions(holdRepository);
    }

    @Test
    void missingRoom_returns404() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.check(roomId));
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(holdRepository);
    }

    private RoomHold approvedHold() {
        RoomHold hold = mock(RoomHold.class);
        when(hold.getId()).thenReturn(holdId);
        when(hold.getCustomerId()).thenReturn(customerId);
        when(hold.getApprovedBy()).thenReturn(UUID.randomUUID());
        when(hold.getApprovedAt()).thenReturn(LocalDateTime.now());
        when(hold.getStatus()).thenReturn(RoomHoldStatus.APPROVED);
        return hold;
    }
}
