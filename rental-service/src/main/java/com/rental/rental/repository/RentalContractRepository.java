package com.rental.rental.repository;

import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface RentalContractRepository
        extends JpaRepository<RentalContract, UUID> {

    @Query("""
        SELECT COUNT(c) > 0
        FROM RentalContract c
        WHERE c.roomId = :roomId
          AND c.status IN :blockingStatuses
          AND c.startDate < :endDate
          AND c.endDate > :startDate
    """)
    boolean existsOverlappingContract(
            @Param("roomId") UUID roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("blockingStatuses")
            java.util.Collection<ContractStatus> blockingStatuses
    );
}
