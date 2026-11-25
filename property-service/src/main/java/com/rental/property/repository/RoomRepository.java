package com.rental.property.repository;

import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByProperty_IdAndCode(UUID propertyId, String code);
    List<Room> findByProperty_IdOrderByCodeAsc(UUID propertyId);
    List<Room> findByPublishedTrueAndStatusOrderByCodeAsc(RoomStatus status);

    // Shared room mutex for ALL write operations: update, publication, viewing and hold.
    // Must be called inside an active @Transactional method. Read-only queries use findById.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findForUpdateById(@Param("id") UUID id);
}
