-- V3: CUSTOMER can request viewings without reserving a room.
-- Auth users are outside Property DB; customer_id and staff_id are UUID references.
CREATE TABLE viewing_appointments (
    id BINARY(16) NOT NULL,
    room_id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    staff_id BINARY(16) NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    result_notes VARCHAR(1000),
    updated_by BINARY(16),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_viewing_appointments PRIMARY KEY (id),
    CONSTRAINT fk_viewings_room FOREIGN KEY (room_id)
        REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT chk_viewings_time CHECK (end_at > start_at),
    CONSTRAINT chk_viewings_status CHECK (status IN (
        'REQUESTED', 'CONFIRMED', 'COMPLETED', 'NO_SHOW', 'CANCELLED')),
    INDEX idx_viewings_customer (customer_id, created_at),
    INDEX idx_viewings_room_time (room_id, start_at, end_at),
    INDEX idx_viewings_staff_time (staff_id, start_at, end_at),
    INDEX idx_viewings_status (status, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
