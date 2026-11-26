ALTER TABLE rental_contracts
    ADD COLUMN hold_id BINARY(16) NULL;
ALTER TABLE rental_contracts
    ADD CONSTRAINT uq_rental_contract_hold_id UNIQUE (hold_id);
