package com.rental.property.service;

import com.rental.property.auth.AuthStaffLookupClient;
import com.rental.property.entity.Property;
import com.rental.property.entity.StaffPropertyAssignment;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class StaffPropertyAssignmentService {
    private final PropertyRepository properties;
    private final StaffPropertyAssignmentRepository assignments;
    private final AuthStaffLookupClient authStaff;

    public StaffPropertyAssignmentService(PropertyRepository properties,
                                          StaffPropertyAssignmentRepository assignments,
                                          AuthStaffLookupClient authStaff) {
        this.properties = properties;
        this.assignments = assignments;
        this.authStaff = authStaff;
    }

    @Transactional
    public void assign(UUID propertyId, UUID staffId, String ownerAuthorization) {
        Property property = properties.findByIdForUpdate(propertyId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        // Do not assign arbitrary UUIDs or users whose Auth role/status changed.
        authStaff.requireActiveStaff(staffId, ownerAuthorization);
        if (!assignments.existsByStaffIdAndProperty_Id(staffId, propertyId)) {
            assignments.saveAndFlush(new StaffPropertyAssignment(staffId, property));
        }
    }

    @Transactional
    public void revoke(UUID propertyId, UUID staffId) {
        if (!properties.existsById(propertyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        }
        assignments.deleteByStaffIdAndProperty_Id(staffId, propertyId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getAssignedStaff(UUID propertyId) {
        if (!properties.existsById(propertyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        }
        return assignments.findByProperty_Id(propertyId).stream()
                .map(StaffPropertyAssignment::getStaffId).toList();
    }
}
