package com.rental.property.auth;

import com.rental.property.repository.StaffPropertyAssignmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropertyAccessServiceTest {
    private StaffPropertyAssignmentRepository assignments;
    private PropertyAccessService access;
    private HttpServletRequest request;
    private final UUID staffId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach void setup() {
        assignments = mock(StaffPropertyAssignmentRepository.class);
        access = new PropertyAccessService(assignments);
        request = mock(HttpServletRequest.class);
        when(request.getAttribute("authenticatedUserId")).thenReturn(staffId);
        when(request.getAttribute("authenticatedRole")).thenReturn("STAFF");
    }

    @Test void assignedStaff_canReadProperty() {
        when(assignments.existsByStaffIdAndProperty_Id(staffId, propertyId)).thenReturn(true);
        assertDoesNotThrow(() -> access.requirePropertyAccess(request, propertyId));
    }
    @Test void unassignedStaff_gets403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requirePropertyAccess(request, propertyId));
        assertEquals(403, ex.getStatusCode().value());
    }
    @Test void owner_canAccessAnyProperty() {
        when(request.getAttribute("authenticatedRole")).thenReturn("OWNER");
        assertDoesNotThrow(() -> access.requirePropertyAccess(request, propertyId));
        verifyNoInteractions(assignments);
    }
    @Test void staffCannotPublish() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requireOwner(request));
        assertEquals(403, ex.getStatusCode().value());
    }
    @Test void missingVerifiedContext_gets401() {
        when(request.getAttribute("authenticatedUserId")).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requirePropertyAccess(request, propertyId));
        assertEquals(401, ex.getStatusCode().value());
    }
    @Test void missingPropertyId_gets400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> access.requirePropertyAccess(request, null));
        assertEquals(400, ex.getStatusCode().value());
    }
}
