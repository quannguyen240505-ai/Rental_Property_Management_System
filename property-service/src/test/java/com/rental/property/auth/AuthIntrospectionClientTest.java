package com.rental.property.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Local fake Auth HTTP server; does not require auth_db, a real JWT or actual Auth Service. */
class AuthIntrospectionClientTest {

    private HttpServer server;
    private AuthIntrospectionClient client;
    private final AtomicReference<String> replyBody = new AtomicReference<>(
            "{\"active\":false,\"userId\":null,\"username\":null,\"role\":null,\"expiresAt\":null}");
    private final AtomicReference<Integer> replyStatus = new AtomicReference<>(200);
    private final AtomicReference<String> rawTokenSeen = new AtomicReference<>();
    private final AtomicReference<String> serviceKeySeen = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/auth/introspect", exchange -> {
            rawTokenSeen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            serviceKeySeen.set(exchange.getRequestHeaders().getFirst("X-Service-Key"));
            byte[] bytes = replyBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(replyStatus.get(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new AuthIntrospectionClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/auth/introspect",
                "test-only-internal-service-key");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void missingBearerHeader_returns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.introspectBearerToken(null));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void activeFalse_returns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.introspectBearerToken("Bearer invalid.jwt.value"));
        assertEquals(401, ex.getStatusCode().value());
        assertEquals("{\"token\":\"invalid.jwt.value\"}", rawTokenSeen.get());
        assertEquals("test-only-internal-service-key", serviceKeySeen.get());
    }

    @Test
    void activeTrue_mapsAuthenticatedUser() {
        replyBody.set("{\"active\":true,\"userId\":\"550e8400-e29b-41d4-a716-446655440000\","
                + "\"username\":\"owner\",\"role\":\"OWNER\",\"expiresAt\":\"2026-11-30T01:00:00Z\"}");
        var result = client.introspectBearerToken("Bearer valid.jwt.value");
        assertTrue(result.active());
        assertEquals("OWNER", result.role());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.userId().toString());
    }

    @Test
    void wrongServiceKeyFromAuth_maps503() {
        replyStatus.set(401);
        replyBody.set("{\"code\":\"INVALID_SERVICE_KEY\",\"message\":\"Invalid internal service credentials\"}");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.introspectBearerToken("Bearer valid.jwt.value"));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    void missingConfiguredServiceKey_maps503() {
        AuthIntrospectionClient unconfigured = new AuthIntrospectionClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/auth/introspect", "");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> unconfigured.introspectBearerToken("Bearer valid.jwt.value"));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    void unreachableAuth_maps503() {
        server.stop(0);
        server = null;
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.introspectBearerToken("Bearer valid.jwt.value"));
        assertEquals(503, ex.getStatusCode().value());
    }
}
