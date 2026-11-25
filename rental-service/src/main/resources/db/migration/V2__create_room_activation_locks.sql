CREATE TABLE room_activation_locks (
                                       room_id BINARY(16) NOT NULL,
                                       CONSTRAINT pk_room_activation_locks
                                           PRIMARY KEY (room_id)
);
