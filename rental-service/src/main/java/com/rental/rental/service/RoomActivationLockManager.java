package com.rental.rental.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.UUID;

@Component
public class RoomActivationLockManager {

    private final JdbcTemplate jdbcTemplate;

    public RoomActivationLockManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockRoom(UUID roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException(
                    "Room ID must not be null"
            );
        }

        byte[] roomIdBytes = toBytes(roomId);

        // Tao ban ghi khoa neu chua ton tai.
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO room_activation_locks (room_id)
                VALUES (?)
                """,
                roomIdBytes
        );

        // Khoa ban ghi phong cho den khi transaction ket thuc.
        jdbcTemplate.queryForObject(
                """
                SELECT room_id
                FROM room_activation_locks
                WHERE room_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getBytes("room_id"),
                roomIdBytes
        );
    }

    private byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
