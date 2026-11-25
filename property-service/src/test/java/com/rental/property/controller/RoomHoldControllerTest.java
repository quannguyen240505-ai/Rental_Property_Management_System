package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.dto.request.ConfirmRoomHoldRequest;
import com.rental.property.dto.response.DepositEligibilityResponse;
import com.rental.property.service.RoomHoldService;
import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private DepositEligibilityResponse eligible(UUID holdId, UUID customerId, UUID propertyId) {
        return new DepositEligibilityResponse(holdId, propertyId, UUID.randomUUID(),
                customerId, RoomHoldStatus.PENDING_VERIFICATION, RoomStatus.RESERVED_PENDING,
                LocalDateTime.now().plusHours(1).atOffset(ZoneOffset.ofHours(7)), true, null);
    }

    @Test void ownerCanReadDepositEligibility() {
        UUID id=UUID.randomUUID();
        when(request.getAttribute("authenticatedRole")).thenReturn("OWNER");
        when(holds.depositEligibility(id)).thenReturn(eligible(id,UUID.randomUUID(),UUID.randomUUID()));
        assertTrue(controller.depositEligibility(id, request).eligible());
    }

    @Test void customerCannotCheckDepositEligibilityEvenForOwnHold() {
        UUID id=UUID.randomUUID();
        when(request.getAttribute("authenticatedRole")).thenReturn("CUSTOMER");
        assertEquals(403,assertThrows(ResponseStatusException.class,
                () -> controller.depositEligibility(id,request)).getStatusCode().value());
        verifyNoInteractions(holds);
    }

    @Test void tenantCannotCheckDepositEligibility() {
        UUID id=UUID.randomUUID();
        when(request.getAttribute("authenticatedRole")).thenReturn("TENANT");
        assertEquals(403,assertThrows(ResponseStatusException.class,
                () -> controller.depositEligibility(id,request)).getStatusCode().value());
        verifyNoInteractions(holds);
    }

    @Test void assignedStaffCanCheckDepositEligibility() {
        UUID id=UUID.randomUUID(), propertyId=UUID.randomUUID();
        when(request.getAttribute("authenticatedRole")).thenReturn("STAFF");
        when(holds.depositEligibility(id)).thenReturn(eligible(id,UUID.randomUUID(),propertyId));
        assertTrue(controller.depositEligibility(id,request).eligible());
        verify(access).requirePropertyAccess(request,propertyId);
    }

    @Test void staffMustHavePropertyAccessForDepositEligibility() {
        UUID id=UUID.randomUUID(), propertyId=UUID.randomUUID();
        when(request.getAttribute("authenticatedRole")).thenReturn("STAFF");
        when(holds.depositEligibility(id)).thenReturn(eligible(id,UUID.randomUUID(),propertyId));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access).requirePropertyAccess(request,propertyId);
        assertEquals(403,assertThrows(ResponseStatusException.class,
                () -> controller.depositEligibility(id,request)).getStatusCode().value());
    }
}
