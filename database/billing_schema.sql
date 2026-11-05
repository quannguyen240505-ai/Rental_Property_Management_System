CREATE DATABASE IF NOT EXISTS rental_billing_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE rental_billing_db;

CREATE TABLE utility_readings (
    id CHAR(36) PRIMARY KEY,
    property_id CHAR(36) NOT NULL,
    room_id CHAR(36) NOT NULL,
    billing_period CHAR(7) NOT NULL,
    utility_type ENUM('ELECTRICITY', 'WATER') NOT NULL,
    previous_reading BIGINT UNSIGNED NOT NULL,
    current_reading BIGINT UNSIGNED NOT NULL,
    evidence_url VARCHAR(500),
    recorded_by CHAR(36) NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_reading_not_decrease
        CHECK (current_reading >= previous_reading),

    CONSTRAINT uk_reading_room_period_type
        UNIQUE (room_id, billing_period, utility_type)
);

CREATE TABLE invoices (
    id CHAR(36) PRIMARY KEY,
    property_id CHAR(36) NOT NULL,
    room_id CHAR(36) NOT NULL,
    contract_id CHAR(36) NOT NULL,
    tenant_user_id CHAR(36) NOT NULL,
    billing_period CHAR(7) NOT NULL,
    status ENUM(
        'DRAFT',
        'ISSUED',
        'PARTIALLY_PAID',
        'PAID',
        'OVERDUE',
        'CANCELLED'
    ) NOT NULL DEFAULT 'DRAFT',
    issue_date DATE,
    due_date DATE,
    total_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    paid_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_invoice_paid_amount
        CHECK (paid_amount <= total_amount),

    CONSTRAINT uk_invoice_contract_period
        UNIQUE (contract_id, billing_period)
);

CREATE TABLE invoice_lines (
    id CHAR(36) PRIMARY KEY,
    invoice_id CHAR(36) NOT NULL,
    line_type ENUM(
        'RENT',
        'ELECTRICITY',
        'WATER',
        'SERVICE_FEE',
        'OTHER'
    ) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(12, 3),
    unit_price BIGINT UNSIGNED,
    amount BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_invoice_line_invoice
        FOREIGN KEY (invoice_id)
        REFERENCES invoices(id)
        ON DELETE RESTRICT
);

CREATE TABLE payment_applications (
    id CHAR(36) PRIMARY KEY,
    invoice_id CHAR(36) NOT NULL,
    source_type ENUM('PAYMENT_SERVICE', 'MANUAL') NOT NULL,
    payment_transaction_id CHAR(36),
    manual_reference VARCHAR(100),
    amount BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    applied_by CHAR(36),
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_application_invoice
        FOREIGN KEY (invoice_id)
        REFERENCES invoices(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_payment_application_transaction
        UNIQUE (payment_transaction_id),

    CONSTRAINT uk_payment_application_idempotency
        UNIQUE (idempotency_key)
);

CREATE INDEX idx_reading_room_period
    ON utility_readings(room_id, billing_period);

CREATE INDEX idx_invoice_property_status
    ON invoices(property_id, status);

CREATE INDEX idx_invoice_tenant_status
    ON invoices(tenant_user_id, status);

CREATE INDEX idx_payment_application_invoice
    ON payment_applications(invoice_id);