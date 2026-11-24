-- Apply once to the existing Billing and Payment databases.
USE rental_billing_db;

ALTER TABLE utility_readings
    ADD COLUMN meter_replaced BOOLEAN NOT NULL DEFAULT FALSE AFTER current_reading,
    ADD COLUMN meter_replacement_reason VARCHAR(500) NULL AFTER meter_replaced,
    ADD CONSTRAINT chk_meter_replacement_reason
        CHECK (meter_replaced = FALSE OR (meter_replacement_reason IS NOT NULL AND CHAR_LENGTH(TRIM(meter_replacement_reason)) > 0));

USE rental_payment_db;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_amount_positive CHECK (amount > 0);

-- Append-only audit history for adjustments to deposit receipts.
CREATE TABLE deposit_receipt_audit_logs (
    id CHAR(36) PRIMARY KEY,
    receipt_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    old_values JSON NOT NULL,
    new_values JSON NOT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_deposit_receipt_audit_receipt
        FOREIGN KEY (receipt_id)
        REFERENCES deposit_receipts(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_deposit_receipt_audit_history
    ON deposit_receipt_audit_logs(receipt_id, changed_at);
