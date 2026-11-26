package com.rental.billing.repository;

import com.rental.billing.entity.UtilityType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UtilityMeterLockRepository {

    private final EntityManager entityManager;

    public UtilityMeterLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String roomId, UtilityType utilityType) {
        // INSERT and the duplicate-key UPDATE both hold an InnoDB exclusive
        // row lock until the surrounding business transaction completes.
        entityManager.createNativeQuery("""
                INSERT INTO utility_meter_write_locks (room_id, utility_type)
                VALUES (:roomId, :utilityType)
                ON DUPLICATE KEY UPDATE room_id = room_id
                """)
                .setParameter("roomId", roomId)
                .setParameter("utilityType", utilityType.name())
                .executeUpdate();
    }
}
