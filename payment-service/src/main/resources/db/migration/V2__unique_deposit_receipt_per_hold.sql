-- Do not delete/reconcile existing duplicate receipts automatically.
-- If duplicates exist, this migration must fail for manual accounting review.
ALTER TABLE deposit_receipts
    ADD CONSTRAINT uk_deposit_receipt_hold UNIQUE (hold_id);

CREATE TABLE deposit_receipt_hold_locks (
    hold_id CHAR(36) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
