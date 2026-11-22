package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.UpdateRoomPublicationRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomPublicationControllerTest {
    private RoomService roomService;
    private PropertyAccessService access;
    private HttpServletRequest request;
    private RoomPublicationController controller;
    private UUID roomId;

    @BeforeEach void setUp() {
        roomService = mock(RoomService.class);
        access = mock(PropertyAccessService.class);
        request = mock(HttpServletRequest.class);
        controller = new RoomPublicationController(roomService, access);
        roomId = UUID.randomUUID();
    }

    @Test void publishRoom_callsServiceAndReturns200() {
        RoomResponse expected = mock(RoomResponse.class);
        when(roomService.setRoomPublished(roomId, true)).thenReturn(expected);
        var result = controller.setPublication(roomId,
                new UpdateRoomPublicationRequest(true), request);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(expected, result.getBody());
        verify(access).requireOwner(request);
        verify(roomService).setRoomPublished(roomId, true);
    }

    @Test void missingPublished_returns400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.setPublication(roomId,
                        new UpdateRoomPublicationRequest(null), request));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(roomService);
    }

    @Test void nullRequest_returns400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.setPublication(roomId, null, request));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(roomService);
    }

    @Test void staffCannotPublish() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requireOwner(request);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.setPublication(roomId,
                        new UpdateRoomPublicationRequest(true), request));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(roomService);
    }
}
