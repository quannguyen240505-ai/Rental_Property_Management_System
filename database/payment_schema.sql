CREATE DATABASE IF NOT EXISTS rental_payment_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE rental_payment_db;

CREATE TABLE payment_transactions (
    id CHAR(36) PRIMARY KEY,
    invoice_id CHAR(36) NOT NULL,
    tenant_user_id CHAR(36) NOT NULL,
    property_id CHAR(36) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    provider_name VARCHAR(50) NOT NULL DEFAULT 'SANDBOX',
    provider_transaction_id VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    status ENUM(
        'PENDING',
        'SUCCESS',
        'FAILED',
        'CANCELLED',
        'TIMEOUT'
    ) NOT NULL DEFAULT 'PENDING',
    callback_verified BOOLEAN NOT NULL DEFAULT FALSE,
    initiated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_payment_amount_positive
        CHECK (amount > 0),

    CONSTRAINT uk_payment_transaction_provider
        UNIQUE (provider_transaction_id),

    CONSTRAINT uk_payment_transaction_idempotency
        UNIQUE (idempotency_key)
);

CREATE TABLE payment_callback_logs (
    id CHAR(36) PRIMARY KEY,
    payment_transaction_id CHAR(36),
    provider_event_id VARCHAR(100) NOT NULL,
    signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSON NOT NULL,
    processing_status ENUM(
        'RECEIVED',
        'PROCESSED',
        'REJECTED',
        'FAILED'
    ) NOT NULL DEFAULT 'RECEIVED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,

    CONSTRAINT uk_callback_event
        UNIQUE (provider_event_id),

    CONSTRAINT fk_callback_transaction
        FOREIGN KEY (payment_transaction_id)
        REFERENCES payment_transactions(id)
        ON DELETE RESTRICT
);

CREATE TABLE deposit_receipts (
    id CHAR(36) PRIMARY KEY,
    receipt_code VARCHAR(50) NOT NULL,
    property_id CHAR(36) NOT NULL,
    room_id CHAR(36) NOT NULL,
    hold_id CHAR(36) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    collected_by CHAR(36) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    payment_method ENUM(
        'CASH',
        'BANK_TRANSFER'
    ) NOT NULL,
    status ENUM(
        'REPORTED',
        'CUSTOMER_CONFIRMED',
        'OWNER_VERIFIED',
        'RECONCILED',
        'TRANSFERRED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'REPORTED',
    evidence_url VARCHAR(500),
    customer_confirmed_at DATETIME,
    owner_verified_by CHAR(36),
    owner_verified_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_deposit_receipt_code
        UNIQUE (receipt_code)
);

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

CREATE TABLE cash_handovers (
    id CHAR(36) PRIMARY KEY,
    property_id CHAR(36) NOT NULL,
    staff_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    total_amount BIGINT UNSIGNED NOT NULL,
    status ENUM(
        'PENDING',
        'RECEIVED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(500),
    handed_over_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE cash_handover_items (
    id CHAR(36) PRIMARY KEY,
    handover_id CHAR(36) NOT NULL,
    receipt_id CHAR(36) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_handover_receipt
        UNIQUE (receipt_id),

    CONSTRAINT fk_handover_item_handover
        FOREIGN KEY (handover_id)
        REFERENCES cash_handovers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_handover_item_receipt
        FOREIGN KEY (receipt_id)
        REFERENCES deposit_receipts(id)
        ON DELETE RESTRICT
);

CREATE TABLE deposit_allocations (
    id CHAR(36) PRIMARY KEY,
    receipt_id CHAR(36) NOT NULL,
    contract_id CHAR(36) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    status ENUM(
        'APPLIED',
        'REVERSED'
    ) NOT NULL DEFAULT 'APPLIED',
    applied_by CHAR(36) NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_deposit_allocation_receipt
        UNIQUE (receipt_id),

    CONSTRAINT fk_deposit_allocation_receipt
        FOREIGN KEY (receipt_id)
        REFERENCES deposit_receipts(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_transaction_invoice
    ON payment_transactions(invoice_id);

CREATE INDEX idx_transaction_tenant
    ON payment_transactions(tenant_user_id);

CREATE INDEX idx_receipt_room_status
    ON deposit_receipts(room_id, status);

CREATE INDEX idx_handover_staff_status
    ON cash_handovers(staff_id, status);

CREATE INDEX idx_allocation_contract
    ON deposit_allocations(contract_id);