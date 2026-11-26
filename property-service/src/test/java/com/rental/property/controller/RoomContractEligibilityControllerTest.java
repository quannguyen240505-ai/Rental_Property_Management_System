package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.response.ContractEligibilityResponse;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.entity.RoomStatus;
import com.rental.property.service.RoomContractEligibilityService;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomContractEligibilityControllerTest {
    private final UUID roomId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private RoomService rooms;
    private PropertyAccessService access;
    private RoomContractEligibilityService eligibility;
    private RoomContractEligibilityController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomService.class);
        access = mock(PropertyAccessService.class);
        eligibility = mock(RoomContractEligibilityService.class);
        request = mock(HttpServletRequest.class);
        controller = new RoomContractEligibilityController(rooms, access, eligibility);
        RoomResponse room = mock(RoomResponse.class);
        when(room.propertyId()).thenReturn(propertyId);
        when(rooms.getRoomById(roomId)).thenReturn(room);
    }

    @Test
    void authorizedRoom_checksPropertyScopeBeforeEligibility() {
        var result = new ContractEligibilityResponse(roomId, propertyId,
                RoomStatus.AVAILABLE, true, null, false, null, null, null);
        when(eligibility.check(roomId)).thenReturn(result);
        var response = controller.getEligibility(roomId, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(result, response.getBody());
        var order = inOrder(rooms, access, eligibility);
        order.verify(rooms).getRoomById(roomId);
        order.verify(access).requirePropertyAccess(request, propertyId);
        order.verify(eligibility).check(roomId);
    }

    @Test
    void staffWithoutPropertyAssignment_returns403WithoutHoldLookup() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requirePropertyAccess(request, propertyId);
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.getEligibility(roomId, request));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(eligibility);
    }

    @Test
    void missingAuthentication_returns401WithoutHoldLookup() {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(access).requirePropertyAccess(request, propertyId);
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.getEligibility(roomId, request));
        assertEquals(401, ex.getStatusCode().value());
        verifyNoInteractions(eligibility);
    }

    @Test
    void missingRoom_returns404() {
        when(rooms.getRoomById(roomId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.getEligibility(roomId, request));
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(access, eligibility);
    }
}
