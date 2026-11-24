-- One stable lock row per meter, including meters with no readings yet.
-- Writes acquire this row before checking periods and continuity, and keep
-- its InnoDB lock until the same business transaction commits or rolls back.
CREATE TABLE utility_meter_write_locks (
    room_id CHAR(36) NOT NULL,
    utility_type ENUM('ELECTRICITY', 'WATER') NOT NULL,
    PRIMARY KEY (room_id, utility_type)
) ENGINE=InnoDB;
