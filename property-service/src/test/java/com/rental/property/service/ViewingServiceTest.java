package com.rental.property.service;

import com.rental.property.dto.request.CreateViewingRequest;
import com.rental.property.dto.response.ViewingResponse;
import com.rental.property.entity.*;
import com.rental.property.repository.RoomRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import com.rental.property.repository.ViewingAppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewingServiceTest {
    @Mock ViewingAppointmentRepository viewings;
    @Mock RoomRepository rooms;
    @Mock StaffPropertyAssignmentRepository assignments;
    @Mock ViewingActorLockService actorLocks;

    private ViewingService service;
    private final UUID roomId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID viewingId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 25, 10, 0);
    private final LocalDateTime end = start.plusMinutes(45);

    @BeforeEach
    void init() {
        service = new ViewingService(viewings, rooms, assignments, actorLocks, clock);
    }

    private Room publishedRoom() {
        Property prop = new Property();
        ReflectionTestUtils.setField(prop, "id", propertyId);
        Room r = new Room();
        ReflectionTestUtils.setField(r, "id", roomId);
        r.setProperty(prop);
        r.setStatus(RoomStatus.AVAILABLE);
        r.setPublished(true);
        return r;
    }

    private ViewingAppointment requested(Room room) {
        ViewingAppointment v = new ViewingAppointment(room, customerId, start, end);
        ReflectionTestUtils.setField(v, "id", viewingId);
        return v;
    }

    private static void status(int code, Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(code, e.getStatusCode().value());
    }

    @Test
    void createRejectsPastTimes() {
        status(400, () -> service.create(new CreateViewingRequest(roomId,
                LocalDateTime.of(2026, 11, 22, 9, 0), end), customerId));
        verifyNoInteractions(viewings, rooms);
    }

    @Test
    void createRejectsUnpublishedRoom() {
        Room r = publishedRoom(); r.setPublished(false);
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(r));
        status(409, () -> service.create(new CreateViewingRequest(roomId,start,end),customerId));
        verify(viewings, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDuplicateActiveRequest() {
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(publishedRoom()));
        when(viewings.countCustomerOverlap(eq(customerId),eq(roomId),anyList(),eq(start),eq(end)))
                .thenReturn(1L);
        status(409, () -> service.create(new CreateViewingRequest(roomId,start,end),customerId));
    }

    @Test
    void createKeepsRoomAvailableAndReturnsRequested() {
        Room r=publishedRoom();
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(r));
        when(viewings.saveAndFlush(any(ViewingAppointment.class))).thenAnswer(inv -> {
            ViewingAppointment v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", viewingId);
            return v;
        });
        ViewingResponse response=service.create(new CreateViewingRequest(roomId,start,end),customerId);
        assertEquals(ViewingStatus.REQUESTED,response.status());
        assertNull(response.staffId());
        assertEquals(RoomStatus.AVAILABLE,r.getStatus());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void onlyOneClaimAllowedForSameViewing() {
        Room r=publishedRoom();
        ViewingAppointment v=requested(r);
        when(assignments.lockByStaffId(staffId)).thenReturn(List.of(new StaffPropertyAssignment(staffId,r.getProperty())));
        when(viewings.findRoomIdById(viewingId)).thenReturn(Optional.of(roomId));
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(r));
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        when(viewings.saveAndFlush(v)).thenReturn(v);
        assertEquals(ViewingStatus.CONFIRMED, service.claim(viewingId,staffId,true).status());
        status(409,()->service.claim(viewingId,staffId,true));
        verify(viewings, times(1)).saveAndFlush(v);
    }

    @Test
    void claimRejectsRoomTimeConflict() {
        Room r=publishedRoom(); ViewingAppointment v=requested(r);
        when(assignments.lockByStaffId(staffId)).thenReturn(List.of(new StaffPropertyAssignment(staffId,r.getProperty())));
        when(viewings.findRoomIdById(viewingId)).thenReturn(Optional.of(roomId));
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(r));
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        when(viewings.countRoomOverlap(roomId,viewingId,ViewingStatus.CONFIRMED,start,end)).thenReturn(1L);
        status(409,()->service.claim(viewingId,staffId,true));
        verify(viewings, never()).saveAndFlush(any());
    }

    @Test
    void claimRejectsStaffAlreadyBusyInAnotherRoom() {
        Room r=publishedRoom(); ViewingAppointment v=requested(r);
        when(assignments.lockByStaffId(staffId)).thenReturn(List.of(new StaffPropertyAssignment(staffId,r.getProperty())));
        when(viewings.findRoomIdById(viewingId)).thenReturn(Optional.of(roomId));
        when(rooms.findForUpdateById(roomId)).thenReturn(Optional.of(r));
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        when(viewings.countStaffOverlap(staffId,viewingId,ViewingStatus.CONFIRMED,start,end)).thenReturn(1L);
        status(409,()->service.claim(viewingId,staffId,true));
    }

    @Test
    void onlyAssignedStaffUpdatesResult() {
        Room r=publishedRoom(); ViewingAppointment v=requested(r);
        v.claim(staffId);
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        status(403,()->service.finish(viewingId,UUID.randomUUID(),false,ViewingStatus.COMPLETED,"Done"));
        verify(viewings, never()).saveAndFlush(any());
    }

    @Test
    void finishSavesNoShow() {
        Room r=publishedRoom(); ViewingAppointment v=requested(r); v.claim(staffId);
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        when(viewings.saveAndFlush(v)).thenReturn(v);
        assertEquals(ViewingStatus.NO_SHOW,
                service.finish(viewingId,staffId,false,ViewingStatus.NO_SHOW,"Customer absent").status());
    }

    @Test
    void customerCannotCancelAnotherUsersViewing() {
        ViewingAppointment v=requested(publishedRoom());
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        status(403,()->service.cancel(viewingId,UUID.randomUUID()));
    }

    @Test
    void customerCanCancelRequestedViewing() {
        ViewingAppointment v=requested(publishedRoom());
        when(viewings.findForUpdate(viewingId)).thenReturn(Optional.of(v));
        when(viewings.saveAndFlush(v)).thenReturn(v);
        assertEquals(ViewingStatus.CANCELLED,service.cancel(viewingId,customerId).status());
    }

    @Test
    void availableForStaffUsesPropertyScope() {
        when(viewings.findByStatusAndRoom_Property_IdInOrderByStartAtAsc(
                ViewingStatus.REQUESTED, List.of(propertyId)))
                .thenReturn(List.of(requested(publishedRoom())));
        assertEquals(1, service.available(false,List.of(propertyId)).size());
        verify(viewings, never()).findByStatusOrderByStartAtAsc(any());
    }
}
