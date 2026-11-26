package com.rental.payment;

import com.rental.payment.config.AuthTokenIntrospector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthTokenIntrospectorConfigTest {
    @Test
    void rejectsMissingOrShortServiceKey() {
        assertThrows(IllegalStateException.class,
                () -> new AuthTokenIntrospector("http://localhost:8081", null));
        assertThrows(IllegalStateException.class,
                () -> new AuthTokenIntrospector("http://localhost:8081", "short"));
    }

    @Test
    void rejectsInvisibleWhitespaceInServiceKey() {
        for (String whitespace : new String[]{" ", "\t", "\n", "\u00a0"}) {
            assertThrows(IllegalStateException.class,
                    () -> new AuthTokenIntrospector("http://localhost:8081",
                            "test-only-internal-service-key-1234567890" + whitespace));
        }
    }

    @Test
    void acceptsTestKeyWithoutContactingAuthAtStartup() {
        assertDoesNotThrow(() -> new AuthTokenIntrospector("http://localhost:8081",
                "test-only-internal-service-key-1234567890"));
    }
}
