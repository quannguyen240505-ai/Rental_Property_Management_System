package com.rental.property.repository;

import com.rental.property.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import com.rental.property.entity.RoomStatus;

import java.util.List;
import java.util.UUID;

public interface RoomRepository
        extends JpaRepository<Room, UUID> {

    boolean existsByProperty_IdAndCode(
            UUID propertyId,
            String code
    );

    List<Room> findByProperty_IdOrderByCodeAsc(
            UUID propertyId
    );

    List<Room> findByPublishedTrueAndStatusOrderByCodeAsc(
            RoomStatus status
    );

}
