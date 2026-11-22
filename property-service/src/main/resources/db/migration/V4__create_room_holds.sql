-- Property owns hold state; Rental owns financial receipt/contract deposit ledger.
-- The application checks room status under a PESSIMISTIC_WRITE room lock.
CREATE TABLE room_holds (
    id BINARY(16) NOT NULL,
    viewing_id BINARY(16) NOT NULL,
    room_id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    created_by BINARY(16) NOT NULL,
    receipt_reference VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    approved_by BINARY(16),
    approved_at DATETIME(6),
    verification_reference VARCHAR(100),
    closed_by BINARY(16),
    closed_at DATETIME(6),
    deposit_resolution_reference VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_room_holds PRIMARY KEY (id),
    CONSTRAINT uq_hold_viewing UNIQUE (viewing_id),
    CONSTRAINT uq_hold_receipt_ref UNIQUE (receipt_reference),
    CONSTRAINT fk_room_holds_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_holds_viewing FOREIGN KEY (viewing_id)
        REFERENCES viewing_appointments(id) ON DELETE RESTRICT,
    CONSTRAINT chk_room_holds_status CHECK (status IN
        ('PENDING_VERIFICATION', 'APPROVED', 'EXPIRED', 'CANCELLED')),
    INDEX idx_room_holds_property (room_id, created_at),
    INDEX idx_room_holds_customer (customer_id, created_at),
    INDEX idx_room_holds_status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
