package com.rental.property.repository;

import com.rental.property.entity.StaffPropertyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StaffPropertyAssignmentRepository extends JpaRepository<StaffPropertyAssignment, UUID> {
    boolean existsByStaffIdAndProperty_Id(UUID staffId, UUID propertyId);
    List<StaffPropertyAssignment> findByStaffId(UUID staffId);
    List<StaffPropertyAssignment> findByProperty_Id(UUID propertyId);
    long deleteByStaffIdAndProperty_Id(UUID staffId, UUID propertyId);
}
