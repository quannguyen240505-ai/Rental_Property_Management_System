package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.dto.request.ConfirmRoomHoldRequest;
import com.rental.property.dto.response.RoomHoldResponse;
import com.rental.property.service.RoomHoldService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomHoldControllerTest {
    private final PropertyAccessService access = mock(PropertyAccessService.class);
    private final RoomHoldService holds = mock(RoomHoldService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final RoomHoldController controller = new RoomHoldController(holds,access);

    @BeforeEach void resetMocks() { reset(access,holds,request); }

    @Test void customerCannotCreateHold() {
        when(request.getAttribute("authenticatedRole")).thenReturn("CUSTOMER");
        var input = new CreateRoomHoldRequest(UUID.randomUUID(),"R",LocalDateTime.now().plusHours(1));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class, ()->controller.create(input, request));
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
        verifyNoInteractions(holds);
    }

    @Test void staffMustHaveAssignedProperty() {
        UUID vid=UUID.randomUUID(),pid=UUID.randomUUID();
        when(holds.viewingPropertyId(vid)).thenReturn(pid);
        when(request.getAttribute("authenticatedRole")).thenReturn("STAFF");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requirePropertyAccess(request,pid);
        var input=new CreateRoomHoldRequest(vid,"R",LocalDateTime.now().plusHours(1));
        assertEquals(403,assertThrows(ResponseStatusException.class,
                ()->controller.create(input,request)).getStatusCode().value());
        verify(holds).viewingPropertyId(vid);
        verify(holds,never()).create(any(),any(),anyBoolean());
    }

    @Test void validStaffPassesAuthorizationBeforeCreatingHold() {
        UUID vid=UUID.randomUUID(), pid=UUID.randomUUID(), staffId=UUID.randomUUID();
        when(holds.viewingPropertyId(vid)).thenReturn(pid);
        when(request.getAttribute("authenticatedRole")).thenReturn("STAFF");
        when(access.verifiedUserId(request)).thenReturn(staffId);
        var input=new CreateRoomHoldRequest(vid,"QA-REC",LocalDateTime.now().plusHours(1));
        assertEquals(201,controller.create(input,request).getStatusCode().value());
        verify(access).requirePropertyAccess(request,pid);
        verify(holds).create(input,staffId,true);
    }

    @Test void staffCannotApproveDeposit() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requireOwner(request);
        assertEquals(403,assertThrows(ResponseStatusException.class,
                ()->controller.approve(UUID.randomUUID(),new ConfirmRoomHoldRequest("VERIFIED"),request))
                .getStatusCode().value());
        verifyNoInteractions(holds);
    }
}
