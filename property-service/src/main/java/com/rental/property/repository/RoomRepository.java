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

    // Used only for serializing claims of different appointments at the same room/time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findForUpdateById(@Param("id") UUID id);
}
