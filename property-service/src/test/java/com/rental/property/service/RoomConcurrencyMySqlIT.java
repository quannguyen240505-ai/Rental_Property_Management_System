package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.entity.Property;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-it")
class RoomConcurrencyMySqlIT {

    @Autowired
    private RoomService roomService;

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID propertyId;

    private enum Result {
        CREATED, CONFLICT
    }

    @BeforeEach
    void setUp() {
        String database = jdbc.queryForObject(
                "SELECT DATABASE()", String.class);

        assertEquals("rental_property_test_db", database,
                "Never run integration tests on the main database");

        Property property = new Property();
        property.setName("Concurrency Test Property");
        property.setAddress("Integration Test Only");

        propertyId = properties.saveAndFlush(property).getId();
    }

    @AfterEach
    void cleanUp() {
        if (propertyId != null) {
            rooms.deleteAll(
                    rooms.findByProperty_IdOrderByCodeAsc(propertyId));
            properties.deleteById(propertyId);
        }
    }

    @Test
    void concurrentCreate_sameRoomCode_onlyOneSucceeds()
            throws Exception {

        CreateRoomRequest request = new CreateRoomRequest(
                propertyId,
                "A101",
                "Room A101",
                null,
                1,
                new BigDecimal("22.50"),
                2000000L
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Result> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Start timeout");
            }

            try {
                roomService.createRoom(request);
                return Result.CREATED;

            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) {
                    return Result.CONFLICT;
                }
                throw ex;
            }
        };

        try {
            Future<Result> first = executor.submit(task);
            Future<Result> second = executor.submit(task);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Result> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

            assertEquals(1, results.stream()
                    .filter(r -> r == Result.CREATED).count());

            assertEquals(1, results.stream()
                    .filter(r -> r == Result.CONFLICT).count());

            assertEquals(1,
                    rooms.findByProperty_IdOrderByCodeAsc(propertyId)
                            .size());

        } finally {
            executor.shutdownNow();
        }
    }
}
