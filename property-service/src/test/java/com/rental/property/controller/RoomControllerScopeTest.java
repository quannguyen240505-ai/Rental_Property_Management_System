package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomControllerScopeTest {
    private RoomService service;
    private PropertyAccessService access;
    private RoomController controller;
    private HttpServletRequest request;
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach void setup() {
        service = mock(RoomService.class);
        access = mock(PropertyAccessService.class);
        controller = new RoomController(service, access);
        request = mock(HttpServletRequest.class);
    }

    @Test void createRoom_checksPropertyScopeBeforeSave() {
        CreateRoomRequest room = new CreateRoomRequest(propertyId,"A101","Room A101",null,1,
                new BigDecimal("20.00"),1000000L);
        RoomResponse expected = mock(RoomResponse.class);
        when(service.createRoom(room)).thenReturn(expected);
        assertEquals(201, controller.createRoom(room, request).getStatusCode().value());
        var order = inOrder(access, service);
        order.verify(access).requirePropertyAccess(request, propertyId);
        order.verify(service).createRoom(room);
    }

    @Test void unauthorizedStaff_cannotCreateRoom() {
        CreateRoomRequest room = new CreateRoomRequest(propertyId,"A101","Room A101",null,1,
                new BigDecimal("20.00"),1000000L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requirePropertyAccess(request, propertyId);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createRoom(room, request));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(service);
    }
}
