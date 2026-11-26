-- Property Service owns the source-of-truth mapping of STAFF to properties.
-- Auth Service remains the authority for roles, account status and JWT.
CREATE TABLE staff_property_assignments (
    id BINARY(16) NOT NULL,
    staff_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    CONSTRAINT pk_staff_property_assignments PRIMARY KEY (id),
    CONSTRAINT uq_staff_property UNIQUE (staff_id, property_id),
    CONSTRAINT fk_staff_property_property FOREIGN KEY (property_id)
        REFERENCES properties(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_staff_property_property ON staff_property_assignments(property_id);
