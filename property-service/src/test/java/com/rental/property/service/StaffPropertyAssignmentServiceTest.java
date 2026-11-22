package com.rental.property.service;

import com.rental.property.auth.AuthStaffLookupClient;
import com.rental.property.entity.Property;
import com.rental.property.entity.StaffPropertyAssignment;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffPropertyAssignmentServiceTest {
    private PropertyRepository properties;
    private StaffPropertyAssignmentRepository assignments;
    private AuthStaffLookupClient auth;
    private StaffPropertyAssignmentService service;
    private final UUID propertyId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final String ownerJwt = "Bearer sample.owner.token";

    @BeforeEach void setup() {
        properties = mock(PropertyRepository.class);
        assignments = mock(StaffPropertyAssignmentRepository.class);
        auth = mock(AuthStaffLookupClient.class);
        service = new StaffPropertyAssignmentService(properties, assignments, auth);
    }

    @Test void assigningValidStaff_savesMapping() {
        Property property = mock(Property.class);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));
        service.assign(propertyId, staffId, ownerJwt);
        verify(auth).requireActiveStaff(staffId, ownerJwt);
        verify(assignments).saveAndFlush(any(StaffPropertyAssignment.class));
    }
    @Test void idempotentAssign_doesNotDuplicate() {
        when(properties.findById(propertyId)).thenReturn(Optional.of(mock(Property.class)));
        when(assignments.existsByStaffIdAndProperty_Id(staffId, propertyId)).thenReturn(true);
        service.assign(propertyId, staffId, ownerJwt);
        verify(assignments, never()).saveAndFlush(any(StaffPropertyAssignment.class));
    }
    @Test void unknownProperty_returns404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assign(propertyId, staffId, ownerJwt));
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(auth, assignments);
    }
    @Test void revokingExistingMapping_callsDelete() {
        when(properties.existsById(propertyId)).thenReturn(true);
        service.revoke(propertyId, staffId);
        verify(assignments).deleteByStaffIdAndProperty_Id(staffId, propertyId);
    }
    @Test void listStaff_returnsIds() {
        when(properties.existsById(propertyId)).thenReturn(true);
        StaffPropertyAssignment assignment = mock(StaffPropertyAssignment.class);
        when(assignment.getStaffId()).thenReturn(staffId);
        when(assignments.findByProperty_Id(propertyId)).thenReturn(List.of(assignment));
        assertEquals(List.of(staffId), service.getAssignedStaff(propertyId));
    }
}
