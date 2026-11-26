package com.rental.payment;

import com.rental.payment.config.SecurityConfig;
import com.rental.payment.controller.DepositReceiptController;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.integration.DepositHoldVerifier;
import com.rental.payment.integration.VerifiedDepositHold;
import com.rental.payment.repository.DepositReceiptRepository;
import com.rental.payment.service.DepositReceiptService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentAuthSecurityTest {
    private static final String KEY = "test-only-internal-service-key-1234567890";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID HOLD_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private HttpServer server;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private DepositReceiptRepository repository;
    private DepositHoldVerifier holdVerifier;
    private volatile int authCalls;
    private volatile int authStatus;
    private volatile String authBody;
    private volatile String receivedMethod;
    private volatile String receivedKey;
    private volatile String receivedBody;
    private volatile String receivedAuthorization;
    private volatile String receivedContentType;
    private volatile boolean delayResponse;
    private final CountDownLatch releaseResponse = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        authStatus = 200;
        authBody = "{\"active\":false}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/auth/introspect", exchange -> {
            authCalls++;
            receivedMethod = exchange.getRequestMethod();
            receivedKey = exchange.getRequestHeaders().getFirst("X-Service-Key");
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (delayResponse) {
                try { releaseResponse.await(6, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = authBody.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(authStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-auth", Map.of(
                "auth.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "auth.service-key", KEY)));
        context.register(TestConfig.class);
        context.refresh();
        repository = context.getBean(DepositReceiptRepository.class);
        holdVerifier = context.getBean(DepositHoldVerifier.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @AfterEach
    void tearDown() {
        releaseResponse.countDown();
        if (context != null) context.close();
        if (server != null) server.stop(0);
    }

    @Test
    void missingBearerReturns401WithoutCallingDependencies() throws Exception {
        mvc.perform(post("/api/deposit-receipts").contentType("application/json").content(requestBody()))
                .andExpect(status().isUnauthorized());
        assertEquals(0, authCalls);
        verifyNoInteractions(repository, holdVerifier);
    }

    @Test
    void inactiveTokenReturns401WithoutWriting() throws Exception {
        expectBlocked(401, "UNAUTHORIZED");
    }

    @Test
    void revokedTokenIsRecheckedOnNextRequest() throws Exception {
        active("OWNER");
        allowHold();
        send().andExpect(status().isCreated());
        clearInvocations(repository, holdVerifier);
        authBody = "{\"active\":false}";
        expectBlocked(401, "UNAUTHORIZED");
        assertEquals(2, authCalls);
    }

    @Test
    void wrongInternalKeyReturns503WithoutWriting() throws Exception {
        authStatus = 401;
        authBody = "{\"code\":\"INVALID_SERVICE_KEY\"}";
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void authServerErrorReturns503WithoutWriting() throws Exception {
        authStatus = 500;
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void connectionFailureReturns503WithoutWriting() throws Exception {
        server.stop(0);
        server = null;
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void readTimeoutReturns503WithoutWriting() throws Exception {
        delayResponse = true;
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void malformedJsonReturns503WithoutWriting() throws Exception {
        authBody = "not-json";
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void incompleteActiveResponseReturns503WithoutWriting() throws Exception {
        authBody = "{\"active\":true}";
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void unknownRoleReturns503WithoutWriting() throws Exception {
        active("ADMIN");
        expectBlocked(503, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void customerReturns403WithoutCallingHoldOrRepository() throws Exception {
        active("CUSTOMER");
        expectBlocked(403, "FORBIDDEN");
    }

    @Test
    void tenantReturns403WithoutCallingHoldOrRepository() throws Exception {
        active("TENANT");
        expectBlocked(403, "FORBIDDEN");
    }

    @Test
    void ownerCanCreateWithVerifiedHoldAndExpectedAuthContract() throws Exception {
        active("OWNER");
        allowHold();
        send().andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectedBy").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.receiptReference").exists());
        assertEquals("POST", receivedMethod);
        assertEquals(KEY, receivedKey);
        assertNull(receivedAuthorization);
        assertEquals("application/json", receivedContentType);
        assertEquals("{\"token\":\"test-user-token\"}", receivedBody);
        var capture = ArgumentCaptor.forClass(DepositReceipt.class);
        verify(repository).insert(capture.capture());
        assertEquals(USER_ID, capture.getValue().collectedBy());
        assertEquals(HOLD_ID, capture.getValue().holdId());
    }

    @Test
    void staffCanCreateOnlyAfterHoldVerification() throws Exception {
        active("STAFF");
        allowHold();
        send().andExpect(status().isCreated());
        var order = inOrder(holdVerifier, repository);
        order.verify(holdVerifier).verifyForCollection(eq(HOLD_ID), any());
        order.verify(repository).insert(any());
    }

    @Test
    void unavailableHoldReturns503WithoutWriting() throws Exception {
        active("STAFF");
        when(holdVerifier.verifyForCollection(eq(HOLD_ID), any()))
                .thenThrow(new ResponseStatusException(SERVICE_UNAVAILABLE));
        send().andExpect(status().isServiceUnavailable());
        verify(repository).lockHold(HOLD_ID);
        verify(repository, never()).insert(any());
        verify(repository, never()).findByHoldIdForUpdate(any());
    }

    @Test
    void invalidAmountReturns400WithoutCallingHoldOrWriting() throws Exception {
        active("OWNER");
        mvc.perform(post("/api/deposit-receipts").header("Authorization", "Bearer test-user-token")
                        .contentType("application/json").content(requestBody().replace("500000", "0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, holdVerifier);
    }

    private void active(String role) {
        authBody = """
                {"active":true,"userId":"%s","username":"test-user","role":"%s","expiresAt":"%s"}
                """.formatted(USER_ID, role, Instant.now().plusSeconds(3600));
    }

    private void allowHold() {
        when(holdVerifier.verifyForCollection(eq(HOLD_ID), any()))
                .thenReturn(new VerifiedDepositHold(HOLD_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "PENDING_VERIFICATION", "RESERVED_PENDING",
                        java.time.OffsetDateTime.now().plusHours(1), true, null));
    }

    private String requestBody() {
        return """
                {"holdId":"%s","amount":500000,"paymentMethod":"CASH"}
                """.formatted(HOLD_ID);
    }

    private org.springframework.test.web.servlet.ResultActions send() throws Exception {
        return mvc.perform(post("/api/deposit-receipts").header("Authorization", "Bearer test-user-token")
                .contentType("application/json").content(requestBody()));
    }

    private void expectBlocked(int status, String code) throws Exception {
        send().andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(repository, holdVerifier);
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, com.rental.payment.controller.PaymentExceptionHandler.class})
    static class TestConfig {
        @Bean DepositReceiptRepository repository() { return mock(DepositReceiptRepository.class); }
        @Bean DepositHoldVerifier holdVerifier() { return mock(DepositHoldVerifier.class); }
        @Bean DepositReceiptService service(DepositReceiptRepository repository, DepositHoldVerifier verifier) {
            return new DepositReceiptService(repository, verifier);
        }
        @Bean DepositReceiptController controller(DepositReceiptService service) {
            return new DepositReceiptController(service);
        }
    }
}
