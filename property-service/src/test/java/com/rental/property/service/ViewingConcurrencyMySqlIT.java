package com.rental.property.service;

import com.rental.property.dto.request.CreateViewingRequest;
import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import com.rental.property.entity.StaffPropertyAssignment;
import com.rental.property.entity.ViewingAppointment;
import com.rental.property.entity.ViewingStatus;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;
import com.rental.property.repository.StaffPropertyAssignmentRepository;
import com.rental.property.repository.ViewingAppointmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-it")
class ViewingConcurrencyMySqlIT {
    @Autowired ViewingService service;
    @Autowired PropertyRepository properties;
    @Autowired RoomRepository rooms;
    @Autowired ViewingAppointmentRepository viewings;
    @Autowired StaffPropertyAssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;

    private UUID propertyId;
    private final List<UUID> customers = new ArrayList<>();
    private final List<UUID> staffIds = new ArrayList<>();
    private final LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            .plusDays(3).withSecond(0).withNano(0);

    private enum Outcome { SUCCESS, CONFLICT }

    @BeforeEach
    void setUp() {
        assertEquals("rental_property_test_db", jdbc.queryForObject("SELECT DATABASE()", String.class),
                "Never run concurrency IT on the main database");
        Property p = new Property();
        p.setName("Viewing Concurrency IT");
        p.setAddress("Integration Test Only");
        propertyId = properties.saveAndFlush(p).getId();
    }

    @AfterEach
    void cleanUp() {
        if (propertyId == null) return;
        for (UUID customer : customers) {
            viewings.deleteAll(viewings.findByCustomerIdOrderByCreatedAtDesc(customer));
        }
        assignments.deleteAll(assignments.findByProperty_Id(propertyId));
        rooms.deleteAll(rooms.findByProperty_IdOrderByCodeAsc(propertyId));
        properties.deleteById(propertyId);
    }

    private Room newRoom(String code) {
        Room r = new Room();
        r.setProperty(properties.findById(propertyId).orElseThrow());
        r.setCode(code);
        r.setTitle(code);
        r.setMonthlyRent(2_000_000L);
        r.setStatus(RoomStatus.AVAILABLE);
        r.setPublished(true);
        return rooms.saveAndFlush(r);
    }

    private UUID newCustomer() {
        UUID id = UUID.randomUUID();
        customers.add(id);
        return id;
    }

    private UUID newStaff() {
        UUID id = UUID.randomUUID();
        staffIds.add(id);
        assignments.saveAndFlush(new StaffPropertyAssignment(
                id, properties.findById(propertyId).orElseThrow()));
        return id;
    }

    private ViewingAppointment newRequest(Room room, UUID customer, int offsetMinutes) {
        return viewings.saveAndFlush(new ViewingAppointment(room, customer,
                start.plusMinutes(offsetMinutes), start.plusMinutes(offsetMinutes + 45)));
    }

    private static Outcome classify(Callable<?> call) throws Exception {
        try {
            call.call();
            return Outcome.SUCCESS;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 409) return Outcome.CONFLICT;
            throw ex;
        }
    }

    private static List<Outcome> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Outcome> a = () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            return classify(first);
        };
        Callable<Outcome> b = () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            return classify(second);
        };
        try {
            Future<Outcome> f1 = pool.submit(a);
            Future<Outcome> f2 = pool.submit(b);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            return List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    private static void assertOneWins(List<Outcome> outcomes) {
        assertEquals(1, outcomes.stream().filter(x -> x == Outcome.SUCCESS).count());
        assertEquals(1, outcomes.stream().filter(x -> x == Outcome.CONFLICT).count());
    }

    @Test
    void concurrentRequests_sameCustomerSameRoom_overlapOnlyOneCreated() throws Exception {
        Room room = newRoom("R101");
        UUID customer = newCustomer();
        CreateViewingRequest request = new CreateViewingRequest(
                room.getId(), start, start.plusMinutes(45));
        List<Outcome> result = race(
                () -> service.create(request, customer),
                () -> service.create(request, customer));
        assertOneWins(result);
        assertEquals(1, viewings.findByCustomerIdOrderByCreatedAtDesc(customer).size());
    }

    @Test
    void concurrentClaims_differentStaffSameRoom_onlyOneConfirmed() throws Exception {
        Room room = newRoom("R102");
        ViewingAppointment first = newRequest(room, newCustomer(), 0);
        ViewingAppointment second = newRequest(room, newCustomer(), 5);
        UUID staffA = newStaff();
        UUID staffB = newStaff();
        List<Outcome> result = race(
                () -> service.claim(first.getId(), staffA, true),
                () -> service.claim(second.getId(), staffB, true));
        assertOneWins(result);
        long confirmed = viewings.findByStatusOrderByStartAtAsc(ViewingStatus.CONFIRMED)
                .stream().filter(v -> v.getId().equals(first.getId()) || v.getId().equals(second.getId()))
                .count();
        assertEquals(1, confirmed);
    }

    @Test
    void concurrentOwnerClaims_twoRoomsSameTime_onlyOneConfirmed() throws Exception {
        ViewingAppointment first = newRequest(newRoom("R103"), newCustomer(), 0);
        ViewingAppointment second = newRequest(newRoom("R104"), newCustomer(), 0);
        UUID owner = UUID.randomUUID();
        List<Outcome> result = race(
                () -> service.claim(first.getId(), owner, false),
                () -> service.claim(second.getId(), owner, false));
        assertOneWins(result);
        long confirmed = viewings.findByStatusOrderByStartAtAsc(ViewingStatus.CONFIRMED)
                .stream().filter(v -> v.getId().equals(first.getId()) || v.getId().equals(second.getId()))
                .count();
        assertEquals(1, confirmed);
    }
}
