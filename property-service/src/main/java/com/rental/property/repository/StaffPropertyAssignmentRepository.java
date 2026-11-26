package com.rental.property.repository;

import com.rental.property.entity.StaffPropertyAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StaffPropertyAssignmentRepository extends JpaRepository<StaffPropertyAssignment, UUID> {
    boolean existsByStaffIdAndProperty_Id(UUID staffId, UUID propertyId);
    List<StaffPropertyAssignment> findByStaffId(UUID staffId);
    List<StaffPropertyAssignment> findByProperty_Id(UUID propertyId);
    long deleteByStaffIdAndProperty_Id(UUID staffId, UUID propertyId);

    // Lock all rows belonging to one staff to serialize claims across multiple rooms.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StaffPropertyAssignment a where a.staffId = :staffId order by a.id")
    List<StaffPropertyAssignment> lockByStaffId(@Param("staffId") UUID staffId);
}
