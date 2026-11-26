CREATE TABLE checkout_settlements (
    id BINARY(16) NOT NULL,
    contract_id BINARY(16) NOT NULL,
    checkout_date DATE NOT NULL,
    applied_deposit BIGINT NOT NULL,
    deduction_amount BIGINT NOT NULL,
    refund_due BIGINT NOT NULL,
    notes VARCHAR(1000),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_checkout_settlements PRIMARY KEY (id),
    CONSTRAINT uq_checkout_contract UNIQUE (contract_id),
    CONSTRAINT fk_checkout_contract FOREIGN KEY (contract_id)
        REFERENCES rental_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_checkout_applied CHECK (applied_deposit >= 0),
    CONSTRAINT chk_checkout_deduction CHECK (deduction_amount >= 0),
    CONSTRAINT chk_checkout_refund CHECK (refund_due >= 0),
    CONSTRAINT chk_checkout_balance CHECK (applied_deposit = deduction_amount + refund_due)
);
