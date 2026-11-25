package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomHold;
import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import com.rental.property.entity.ViewingAppointment;
import com.rental.property.entity.ViewingStatus;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomHoldRepository;
import com.rental.property.repository.RoomRepository;
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
class RoomHoldConcurrencyMySqlIT {
    @Autowired RoomHoldService service;
    @Autowired PropertyRepository properties;
    @Autowired RoomRepository rooms;
    @Autowired ViewingAppointmentRepository viewings;
    @Autowired RoomHoldRepository holds;
    @Autowired JdbcTemplate jdbc;

    private UUID propertyId;
    private final List<UUID> customers = new ArrayList<>();
    private final LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            .plusDays(3).withSecond(0).withNano(0);
    private final UUID staff = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    private enum Outcome { SUCCESS, CONFLICT }

    @BeforeEach
    void setUp() {
        assertEquals("rental_property_test_db", jdbc.queryForObject("SELECT DATABASE()", String.class),
                "Never run concurrency IT on main database");
        Property p = new Property();
        p.setName("Room Hold Concurrency IT");
        p.setAddress("Integration Test Only");
        propertyId = properties.saveAndFlush(p).getId();
    }

    @AfterEach
    void cleanUp() {
        if (propertyId == null) return;
        holds.deleteAll(holds.findByRoom_Property_IdOrderByCreatedAtDesc(propertyId));
        for (UUID customer : customers) {
            viewings.deleteAll(viewings.findByCustomerIdOrderByCreatedAtDesc(customer));
        }
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

    private ViewingAppointment completed(Room room) {
        UUID customer = UUID.randomUUID();
        customers.add(customer);
        ViewingAppointment v = new ViewingAppointment(room, customer, start, start.plusMinutes(45));
        v.claim(staff);
        v.finish(ViewingStatus.COMPLETED, "Viewing complete", staff);
        return viewings.saveAndFlush(v);
    }

    private CreateRoomHoldRequest request(ViewingAppointment viewing, String reference) {
        return new CreateRoomHoldRequest(viewing.getId(), reference,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(1));
    }

    private RoomHold createHold(Room room, String receipt) {
        ViewingAppointment viewing = completed(room);
        var response = service.create(request(viewing, receipt), owner, false);
        return holds.findById(response.id()).orElseThrow();
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

    private static void oneWins(List<Outcome> outcomes) {
        assertEquals(1, outcomes.stream().filter(x -> x == Outcome.SUCCESS).count());
        assertEquals(1, outcomes.stream().filter(x -> x == Outcome.CONFLICT).count());
    }

    @Test
    void twoViewingsSameRoom_onlyOneHoldCreated() throws Exception {
        Room room = newRoom("H101");
        ViewingAppointment first = completed(room);
        ViewingAppointment second = completed(room);
        oneWins(race(
                () -> service.create(request(first, "REC-H101-A"), owner, false),
                () -> service.create(request(second, "REC-H101-B"), owner, false)));
        assertEquals(1, holds.findByRoom_Property_IdOrderByCreatedAtDesc(propertyId).size());
        assertEquals(RoomStatus.RESERVED_PENDING, rooms.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void sameReceiptDifferentRooms_failedRoomRemainsAvailable() throws Exception {
        Room roomA = newRoom("H102");
        Room roomB = newRoom("H103");
        ViewingAppointment first = completed(roomA);
        ViewingAppointment second = completed(roomB);
        oneWins(race(
                () -> service.create(request(first, "REC-SHARED"), owner, false),
                () -> service.create(request(second, "REC-SHARED"), owner, false)));
        assertEquals(1, holds.findByRoom_Property_IdOrderByCreatedAtDesc(propertyId).size());
        long available = List.of(rooms.findById(roomA.getId()).orElseThrow(),
                rooms.findById(roomB.getId()).orElseThrow()).stream()
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        assertEquals(1, available, "Rejected receipt must roll back the failed room update");
    }

    @Test
    void twoApprovalsSameHold_onlyOneSucceeds() throws Exception {
        Room room = newRoom("H104");
        RoomHold hold = createHold(room, "REC-H104");
        oneWins(race(
                () -> service.approve(hold.getId(), owner, "PROOF-A"),
                () -> service.approve(hold.getId(), owner, "PROOF-B")));
        assertEquals(RoomHoldStatus.APPROVED, holds.findById(hold.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.RESERVED, rooms.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void approveAndCancelAtOnce_noInternalErrorAndRoomConsistent() throws Exception {
        Room room = newRoom("H105");
        RoomHold hold = createHold(room, "REC-H105");
        List<Outcome> outcomes = race(
                () -> service.approve(hold.getId(), owner, "PROOF-105"),
                () -> service.cancel(hold.getId(), owner, "RESOLUTION-105"));
        // If approve commits first, cancel may then legally close the approved hold.
        assertTrue(outcomes.stream().anyMatch(o -> o == Outcome.SUCCESS));
        RoomHold fresh = holds.findById(hold.getId()).orElseThrow();
        RoomStatus actual = rooms.findById(room.getId()).orElseThrow().getStatus();
        if (fresh.getStatus() == RoomHoldStatus.APPROVED) {
            assertEquals(RoomStatus.RESERVED, actual);
        } else {
            assertEquals(RoomHoldStatus.CANCELLED, fresh.getStatus());
            assertEquals(RoomStatus.AVAILABLE, actual);
        }
    }

    @Test
    void cancelAndExpireAtOnce_onlyOneTerminalTransition() throws Exception {
        Room room = newRoom("H106");
        RoomHold hold = createHold(room, "REC-H106");
        // Deliberately expire this test hold; no sleep or production schedule changes.
        // UUID stored in MySQL BINARY(16) follows Hibernate's standard 16-byte UUID mapping.
        byte[] id = java.nio.ByteBuffer.allocate(16)
                .putLong(hold.getId().getMostSignificantBits())
                .putLong(hold.getId().getLeastSignificantBits()).array();
        jdbc.update("UPDATE room_holds SET expires_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(1), id);
        oneWins(race(
                () -> service.cancel(hold.getId(), owner, "RESOLUTION-106"),
                () -> service.expire(hold.getId(), owner, "EXPIRED-106")));
        RoomHold fresh = holds.findById(hold.getId()).orElseThrow();
        assertTrue(fresh.getStatus() == RoomHoldStatus.CANCELLED
                || fresh.getStatus() == RoomHoldStatus.EXPIRED);
        assertEquals(RoomStatus.AVAILABLE, rooms.findById(room.getId()).orElseThrow().getStatus());
    }
}
