package com.rental.billing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import com.rental.billing.config.SecurityConfig;
import com.rental.billing.controller.MeterReadingController;
import com.rental.billing.repository.UtilityReadingRepository;
import com.rental.billing.repository.UtilityMeterLockRepository;
import com.rental.billing.service.MeterReadingService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BillingAuthSecurityTest {

    private static final String KEY =
            "test-only-internal-service-key-1234567890";
    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";
    private static final String ROOM_ID =
            "22222222-2222-4222-8222-222222222222";

    private HttpServer authServer;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UtilityReadingRepository repository;
    private UtilityMeterLockRepository meterLocks;

    private volatile CountDownLatch authResponseGate;
    private int authStatus;
    private String authBody;
    private int authCalls;
    private String receivedKey;
    private String receivedBody;
    private String receivedAuthorization;
    private String receivedMethod;

    @BeforeEach
    void setUp() throws Exception {
        authStatus = 200;
        authBody = "{\"active\":false}";
        authCalls = 0;

        authServer = HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);

        authServer.createContext("/internal/auth/introspect", exchange -> {
            authCalls++;
            receivedMethod = exchange.getRequestMethod();
            receivedKey = exchange.getRequestHeaders()
                    .getFirst("X-Service-Key");
            receivedAuthorization = exchange.getRequestHeaders()
                    .getFirst("Authorization");
            receivedBody = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            CountDownLatch gate = authResponseGate;
            if (gate != null) {
                try {
                    gate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
            }

            byte[] bytes = authBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(authStatus, bytes.length);

            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        authServer.start();

        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test-auth", Map.of(
                        "auth.base-url",
                        "http://127.0.0.1:" + authServer.getAddress().getPort(),
                        "auth.service-key", KEY)));
        context.register(TestConfig.class);
        context.refresh();

        repository = context.getBean(UtilityReadingRepository.class);
        meterLocks = context.getBean(UtilityMeterLockRepository.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        if (authServer != null) {
            authServer.stop(0);
        }
    }

    @Test
    void missingTokenReturns401WithoutCallingAuthOrRepository()
            throws Exception {
        mvc.perform(get("/api/meter-readings")
                        .param("roomId", ROOM_ID)
                        .param("period", "2026-01"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, authCalls);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void inactiveTokenReturns401BeforeAnyWrite() throws Exception {
        postReading(401);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void invalidServiceKeyReturns503BeforeAnyWrite() throws Exception {
        authStatus = 401;
        authBody = "{\"code\":\"INVALID_SERVICE_KEY\"}";

        postReading(503);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void authServerErrorReturns503BeforeAnyWrite() throws Exception {
        authStatus = 500;

        postReading(503);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void authConnectionFailureReturns503BeforeAnyWrite()
            throws Exception {
        authServer.stop(0);
        authServer = null;

        postReading(503);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void invalidJsonReturns503BeforeAnyWrite() throws Exception {
        authBody = "not-json";

        postReading(503);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void incompleteActiveResponseReturns503BeforeAnyWrite()
            throws Exception {
        authBody = "{\"active\":true}";

        postReading(503);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void staffReturns403BeforeAnyWrite() throws Exception {
        activeUser("STAFF");

        postReading(403);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void ownerCanReadAndAuthReceivesExpectedContract()
            throws Exception {
        activeUser("OWNER");

        when(repository.findByRoomIdAndBillingPeriodOrderByUtilityTypeAsc(
                ROOM_ID, "2026-01")).thenReturn(List.of());

        mvc.perform(get("/api/meter-readings")
                        .header("Authorization", "Bearer test-user-token")
                        .param("roomId", ROOM_ID)
                        .param("period", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        assertEquals(1, authCalls);
        assertEquals("POST", receivedMethod);
        assertEquals(KEY, receivedKey);
        assertNull(receivedAuthorization);
        assertTrue(receivedBody.contains("\"token\":\"test-user-token\""));

        verify(repository)
                .findByRoomIdAndBillingPeriodOrderByUtilityTypeAsc(
                        ROOM_ID, "2026-01");
    }

    @Test
    void customerReturns403BeforeAnyWrite() throws Exception {
        activeUser("CUSTOMER");

        postReading(403);
        verifyNoInteractions(repository, meterLocks);
    }

    @Test
    void authReadTimeoutReturns503BeforeAnyWrite() throws Exception {
        activeUser("OWNER");

        CountDownLatch gate = new CountDownLatch(1);
        authResponseGate = gate;

        try {
            postReading(503);

            assertEquals(1, authCalls);
            verifyNoInteractions(repository, meterLocks);
        } finally {
            gate.countDown();
        }
    }

    private void activeUser(String role) {
        authBody = """
                {
                  "active": true,
                  "userId": "%s",
                  "username": "test-user",
                  "role": "%s",
                  "expiresAt": "%s"
                }
                """.formatted(
                        USER_ID, role, Instant.now().plusSeconds(3600));
    }

    private void postReading(int expectedStatus) throws Exception {
        mvc.perform(post("/api/meter-readings")
                        .header("Authorization", "Bearer test-user-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "roomId": "%s",
                                  "billingPeriod": "2026-01",
                                  "utilityType": "ELECTRICITY",
                                  "previousReading": 100,
                                  "currentReading": 150,
                                  "meterReplaced": false
                                }
                                """.formatted(USER_ID, ROOM_ID)))
                .andExpect(status().is(expectedStatus));
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        UtilityReadingRepository repository() {
            return mock(UtilityReadingRepository.class);
        }

        @Bean
        UtilityMeterLockRepository meterLocks() {
            return mock(UtilityMeterLockRepository.class);
        }

        @Bean
        MeterReadingService service(
                UtilityReadingRepository repository,
                UtilityMeterLockRepository meterLocks) {
            return new MeterReadingService(repository, meterLocks);
        }

        @Bean
        MeterReadingController controller(MeterReadingService service) {
            return new MeterReadingController(service);
        }
    }
}
