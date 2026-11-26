package com.rental.payment;

import com.rental.payment.integration.PropertyDepositHoldVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDepositHoldVerifierTest {
    private static final UUID HOLD = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private HttpServer server;
    private PropertyDepositHoldVerifier verifier;
    private volatile int status;
    private volatile String body;
    private volatile String authorization, method, serviceKey, path, requestBody;
    private volatile boolean slow;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach void setUp() throws Exception {
        status = 200;
        body = response(true, null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/room-holds/", exchange -> {
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().toString();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            serviceKey = exchange.getRequestHeaders().getFirst("X-Service-Key");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (slow) {
                try { release.await(6, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        verifier = new PropertyDepositHoldVerifier("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void tearDown() {
        release.countDown();
        if (server != null) server.stop(0);
    }

    @Test void forwardsUserJwtWithGetAndNoServiceSecretOrBody() {
        var result = verifier.verifyForCollection(HOLD, authentication());
        assertTrue(result.eligible());
        assertEquals("GET", method);
        assertEquals("/api/room-holds/" + HOLD + "/deposit-eligibility", path);
        assertEquals("Bearer test-user-jwt", authorization);
        assertNull(serviceKey);
        assertEquals("", requestBody);
        assertEquals(HOLD, result.holdId());
        assertNotNull(result.expiresAt().getOffset());
    }
    @Test void preservesBusinessDenialForServiceToHandleRetry() {
        body = response(false, "RECEIPT_ALREADY_EXISTS");
        var result = verifier.verifyForCollection(HOLD, authentication());
        assertFalse(result.eligible());
        assertEquals("RECEIPT_ALREADY_EXISTS", result.reason());
    }
    @Test void propagates401() { status = 401; expectStatus(401); }
    @Test void propagates403() { status = 403; expectStatus(403); }
    @Test void propagates404() { status = 404; expectStatus(404); }
    @Test void maps500To503() { status = 500; expectStatus(503); }
    @Test void mapsUnexpected400To503() { status = 400; expectStatus(503); }
    @Test void rejectsMalformedJson() { body = "not-json"; expectStatus(503); }
    @Test void rejectsIncompleteResponse() { body = "{\"eligible\":true}"; expectStatus(503); }
    @Test void rejectsDifferentHoldId() { body = body.replace(HOLD.toString(), UUID.randomUUID().toString()); expectStatus(503); }
    @Test void rejectsExpiryWithoutOffset() { body = body.replaceAll("[+-]\\d{2}:\\d{2}(?=\")", ""); expectStatus(503); }
    @Test void connectionFailureReturns503() { server.stop(0); server = null; expectStatus(503); }
    @Test void timeoutReturns503() { slow = true; expectStatus(503); }
    @Test void rejectsMissingBearerAuthentication() {
        var ex = assertThrows(ResponseStatusException.class, () -> verifier.verifyForCollection(HOLD, null));
        assertEquals(401, ex.getStatusCode().value());
        assertNull(method);
    }
    @Test void rejectsMalformedBaseUrlAtStartup() {
        for (String url : List.of("http://localhost: 8082", "localhost:8082", "http://localhost:8082/api/room-holds", "http://user@localhost:8082")) {
            assertThrows(IllegalStateException.class, () -> new PropertyDepositHoldVerifier(url));
        }
    }

    private void expectStatus(int code) {
        var ex = assertThrows(ResponseStatusException.class, () -> verifier.verifyForCollection(HOLD, authentication()));
        assertEquals(code, ex.getStatusCode().value());
    }
    private BearerTokenAuthentication authentication() {
        List<org.springframework.security.core.GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_STAFF"));
        var principal = new DefaultOAuth2AuthenticatedPrincipal(UUID.randomUUID().toString(), Map.<String, Object>of("role", "STAFF"), authorities);
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-user-jwt", Instant.now(), Instant.now().plusSeconds(3600));
        return new BearerTokenAuthentication(principal, token, authorities);
    }
    private String response(boolean eligible, String reason) {
        return """
                {"holdId":"%s","propertyId":"22222222-2222-4222-8222-222222222222",
                 "roomId":"33333333-3333-4333-8333-333333333333",
                 "customerId":"44444444-4444-4444-8444-444444444444",
                 "holdStatus":"PENDING_VERIFICATION","roomStatus":"RESERVED_PENDING",
                 "expiresAt":"%s","eligible":%s,"reason":%s}
                """.formatted(HOLD, OffsetDateTime.now(java.time.ZoneOffset.ofHours(7)).plusHours(1), eligible,
                        reason == null ? "null" : "\"" + reason + "\"");
    }
}
