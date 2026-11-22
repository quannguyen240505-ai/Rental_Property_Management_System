package com.rental.property.controller;

import com.rental.property.dto.request.UpdateRoomPublicationRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomPublicationControllerTest {

    private RoomService roomService;
    private RoomPublicationController controller;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        controller = new RoomPublicationController(roomService);
        roomId = UUID.randomUUID();
    }

    @Test
    void publishRoom_callsServiceAndReturns200() {
        RoomResponse expected = mock(RoomResponse.class);
        when(roomService.setRoomPublished(roomId, true)).thenReturn(expected);

        var result = controller.setPublication(
                roomId, new UpdateRoomPublicationRequest(true)
        );

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
        verify(roomService).setRoomPublished(roomId, true);
    }

    @Test
    void missingPublished_returns400() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.setPublication(
                        roomId, new UpdateRoomPublicationRequest(null))
        );
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(roomService);
    }

    @Test
    void nullRequest_returns400() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.setPublication(roomId, null)
        );
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(roomService);
    }
}
