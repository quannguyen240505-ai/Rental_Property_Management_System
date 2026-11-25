package com.rental.property.repository;

import com.rental.property.entity.RoomHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomHoldRepository extends JpaRepository<RoomHold, UUID> {
    boolean existsByViewing_Id(UUID viewingId);
    boolean existsByReceiptReference(String receiptReference);

    List<RoomHold> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<RoomHold> findByRoom_Property_IdOrderByCreatedAtDesc(UUID propertyId);

    @Query("select h.room.id from RoomHold h where h.id = :id")
    Optional<UUID> findRoomIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from RoomHold h where h.id = :id")
    Optional<RoomHold> findForUpdate(@Param("id") UUID id);
}
