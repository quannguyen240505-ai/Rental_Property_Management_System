package com.rental.property.repository;

import com.rental.property.entity.ViewingAppointment;
import com.rental.property.entity.ViewingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViewingAppointmentRepository extends JpaRepository<ViewingAppointment, UUID> {
    List<ViewingAppointment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<ViewingAppointment> findByStatusOrderByStartAtAsc(ViewingStatus status);
    List<ViewingAppointment> findByStatusAndRoom_Property_IdInOrderByStartAtAsc(
            ViewingStatus status, List<UUID> propertyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ViewingAppointment v where v.id = :id")
    Optional<ViewingAppointment> findForUpdate(@Param("id") UUID id);

    @Query("select count(v) from ViewingAppointment v " +
            "where v.customerId = :customerId and v.room.id = :roomId " +
            "and v.status in :statuses and v.startAt < :endAt and v.endAt > :startAt")
    long countCustomerOverlap(@Param("customerId") UUID customerId,
                              @Param("roomId") UUID roomId,
                              @Param("statuses") List<ViewingStatus> statuses,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endAt") LocalDateTime endAt);

    @Query("select count(v) from ViewingAppointment v " +
            "where v.room.id = :roomId and v.id <> :excludeId " +
            "and v.status = :status and v.startAt < :endAt and v.endAt > :startAt")
    long countRoomOverlap(@Param("roomId") UUID roomId,
                          @Param("excludeId") UUID excludeId,
                          @Param("status") ViewingStatus status,
                          @Param("startAt") LocalDateTime startAt,
                          @Param("endAt") LocalDateTime endAt);

    @Query("select count(v) from ViewingAppointment v " +
            "where v.staffId = :staffId and v.id <> :excludeId " +
            "and v.status = :status and v.startAt < :endAt and v.endAt > :startAt")
    long countStaffOverlap(@Param("staffId") UUID staffId,
                           @Param("excludeId") UUID excludeId,
                           @Param("status") ViewingStatus status,
                           @Param("startAt") LocalDateTime startAt,
                           @Param("endAt") LocalDateTime endAt);
}
