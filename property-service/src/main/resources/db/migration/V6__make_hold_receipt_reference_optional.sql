-- Payment creates and verifies receipts AFTER Property creates the hold.
-- Keep the existing UNIQUE index: MySQL permits multiple NULL values.
-- V1..V5 must remain unchanged to preserve Flyway checksums.
ALTER TABLE room_holds
    MODIFY COLUMN receipt_reference VARCHAR(100) NULL;
