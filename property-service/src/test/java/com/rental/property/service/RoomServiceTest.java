package com.rental.property.service;

import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {

    private RoomRepository roomRepository;
    private RoomService roomService;

    private UUID roomId;
    private Room room;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        PropertyRepository propertyRepository =
                mock(PropertyRepository.class);

        roomService = new RoomService(
                roomRepository, propertyRepository
        );

        roomId = UUID.randomUUID();

        Property property = new Property();
        property.setName("Khu tro Minh Quan");

        room = new Room();
        room.setProperty(property);
        room.setCode("P101");
        room.setTitle("Phong 101");
        room.setMonthlyRent(2800000L);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setPublished(true);
    }

    @Test
    void publicRoom_availableAndPublished_returnsResponse() {
        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        var response = roomService.getPublicRoomById(roomId);

        assertEquals("P101", response.code());
        assertEquals("Phong 101", response.title());
    }

    @Test
    void publicRoom_unpublished_returns404() {
        room.setPublished(false);

        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> roomService.getPublicRoomById(roomId)
                );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void publicRoom_occupied_returns404() {
        room.setStatus(RoomStatus.OCCUPIED);

        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> roomService.getPublicRoomById(roomId)
                );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void publicRoom_notFound_returns404() {
        when(roomRepository.findById(roomId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> roomService.getPublicRoomById(roomId)
                );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void publishRoom_available_returnsPublishedTrue() {
        room.setPublished(false);

        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room))
                .thenReturn(room);

        var response = roomService.setRoomPublished(roomId, true);

        assertEquals(true, response.published());
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void unpublishRoom_returnsPublishedFalse() {
        room.setPublished(true);

        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room))
                .thenReturn(room);

        var response = roomService.setRoomPublished(roomId, false);

        assertEquals(false, response.published());
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void publishRoom_occupied_returns409() {
        room.setStatus(RoomStatus.OCCUPIED);

        when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roomService.setRoomPublished(roomId, true)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(roomRepository, never()).saveAndFlush(any(Room.class));
    }

    @Test
    void publishRoom_notFound_returns404() {
        when(roomRepository.findById(roomId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roomService.setRoomPublished(roomId, true)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(roomRepository, never()).saveAndFlush(any(Room.class));
    }

}
