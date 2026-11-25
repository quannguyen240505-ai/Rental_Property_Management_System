
package com.rental.property.service;

import com.rental.property.auth.AuthStaffLookupClient;
import com.rental.property.entity.Property;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-it")
class StaffAssignmentConcurrencyMySqlIT {

    @Autowired
    private StaffPropertyAssignmentService service;

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private StaffPropertyAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AuthStaffLookupClient authStaff;

    private UUID propertyId;
    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String db = jdbc.queryForObject(
                "SELECT DATABASE()", String.class);

        assertEquals("rental_property_test_db", db);

        Property property = new Property();
        property.setName("STAFF Concurrency Test");
        property.setAddress("Test Database Only");

        propertyId = properties.saveAndFlush(property).getId();
    }

    @AfterEach
    void cleanUp() {
        if (propertyId != null) {
            assignments.deleteAll(
                    assignments.findByProperty_Id(propertyId));
            properties.deleteById(propertyId);
        }
    }

    @Test
    void concurrentAssign_sameStaff_onlyOneMapping()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Void> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Start timeout");
            }

            service.assign(
                    propertyId, staffId, "Bearer test-owner-token");

            return null;
        };

        try {
            Future<Void> first = executor.submit(task);
            Future<Void> second = executor.submit(task);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            long count = assignments
                    .findByProperty_Id(propertyId)
                    .stream()
                    .filter(a -> staffId.equals(a.getStaffId()))
                    .count();

            assertEquals(1, count);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
