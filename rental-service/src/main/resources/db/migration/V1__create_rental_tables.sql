CREATE TABLE tenant_profiles (
    id BINARY(16) NOT NULL,
    user_id BINARY(16),
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tenant_profiles PRIMARY KEY (id)
);
CREATE TABLE rental_contracts (
    id BINARY(16) NOT NULL,
    room_id BINARY(16) NOT NULL,
    tenant_profile_id BINARY(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rent_amount BIGINT NOT NULL,
    deposit_required BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_rental_contracts PRIMARY KEY (id),
    CONSTRAINT fk_contract_tenant FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT chk_contract_dates CHECK (end_date > start_date),
    CONSTRAINT chk_contract_rent CHECK (rent_amount >= 0),
    CONSTRAINT chk_contract_deposit CHECK (deposit_required >= 0)
);
CREATE TABLE contract_deposits (
    id BINARY(16) NOT NULL,
    contract_id BINARY(16) NOT NULL,
    source_receipt_id BINARY(16),
    applied_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_contract_deposits PRIMARY KEY (id),
    CONSTRAINT fk_deposit_contract FOREIGN KEY (contract_id) REFERENCES rental_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT uq_contract_deposit_source_receipt UNIQUE (source_receipt_id),
    CONSTRAINT chk_applied_amount CHECK (applied_amount >= 0)
);