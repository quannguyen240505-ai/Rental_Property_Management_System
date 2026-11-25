-- V5: transaction-scoped mutex for every viewing claimant (STAFF or OWNER).
-- Auth users are outside this database: store UUIDs, no cross-service foreign key.
CREATE TABLE viewing_actor_locks (
    actor_id BINARY(16) NOT NULL,
    CONSTRAINT pk_viewing_actor_locks PRIMARY KEY (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
