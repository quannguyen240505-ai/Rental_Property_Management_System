package com.rental.property.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Stable DB mutex per claim actor, shared by OWNER and STAFF across JVMs. */
@Component
public class ViewingActorLockService {
    private final JdbcTemplate jdbc;

    public ViewingActorLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Must run on the SAME transaction/connection as ViewingService.claim().
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID actorId) {
        if (actorId == null) throw new IllegalArgumentException("actorId required");
        byte[] id = ByteBuffer.allocate(16)
                .putLong(actorId.getMostSignificantBits())
                .putLong(actorId.getLeastSignificantBits()).array();

        // Upsert acquires an exclusive InnoDB PK lock even if the row exists.
        // Avoid INSERT IGNORE followed by FOR UPDATE (shared-to-exclusive upgrade deadlocks).
        // The exclusive record lock is held until the enclosing claim transaction commits.
        jdbc.update("INSERT INTO viewing_actor_locks(actor_id) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE actor_id = actor_id", id);
    }
}
