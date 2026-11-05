-- V1: Initial Property Service schema

CREATE TABLE properties (
                            id BINARY(16) NOT NULL,
                            name VARCHAR(150) NOT NULL,
                            address VARCHAR(255) NOT NULL,
                            description TEXT,
                            contact_phone VARCHAR(20),

                            created_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),
                            updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                            CONSTRAINT pk_properties PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;


CREATE TABLE rooms (
                       id BINARY(16) NOT NULL,
                       property_id BINARY(16) NOT NULL,

                       code VARCHAR(30) NOT NULL,
                       title VARCHAR(150) NOT NULL,
                       description TEXT,

                       floor_number INT,
                       area_m2 DECIMAL(8,2),
                       monthly_rent BIGINT NOT NULL,

                       status VARCHAR(32) NOT NULL
                                                  DEFAULT 'AVAILABLE',
                       published BOOLEAN NOT NULL DEFAULT FALSE,

                       created_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),
                       updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                       CONSTRAINT pk_rooms
                           PRIMARY KEY (id),

                       CONSTRAINT uq_rooms_property_code
                           UNIQUE (property_id, code),

                       CONSTRAINT fk_rooms_property
                           FOREIGN KEY (property_id)
                               REFERENCES properties(id)
                               ON DELETE RESTRICT,

                       CONSTRAINT chk_rooms_rent
                           CHECK (monthly_rent >= 0),

                       CONSTRAINT chk_rooms_area
                           CHECK (area_m2 IS NULL OR area_m2 > 0),

                       CONSTRAINT chk_rooms_status
                           CHECK (status IN (
                                             'AVAILABLE',
                                             'RESERVED_PENDING',
                                             'RESERVED',
                                             'OCCUPIED'
                               ))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;
