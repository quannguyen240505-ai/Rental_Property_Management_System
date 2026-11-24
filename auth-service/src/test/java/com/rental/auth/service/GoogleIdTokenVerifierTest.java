package com.rental.auth.service;

import com.rental.auth.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GoogleIdTokenVerifierTest {
    private static final String CLIENT = "client.apps.googleusercontent.com";

    private Jwt token(String audience, String issuer, boolean verified, Instant expires) {
        return Jwt.withTokenValue("test.jwt")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("google-sub-123")
                .audience(List.of(audience))
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(expires)
                .claim("email", "Customer@Gmail.com")
                .claim("email_verified", verified)
                .claim("name", "Test Customer")
                .build();
    }

    @Test void validToken_returnsVerifiedIdentity() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT,
                raw -> token(CLIENT, "https://accounts.google.com", true, Instant.now().plusSeconds(300)));
        var id = verifier.verify("signed.jwt");
        assertEquals("google-sub-123", id.subject());
        assertEquals("customer@gmail.com", id.email());
    }

    @Test void wrongAudience_isRejected() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT,
                raw -> token("attacker.apps.googleusercontent.com", "https://accounts.google.com", true,
                        Instant.now().plusSeconds(300)));
        assertEquals("INVALID_GOOGLE_TOKEN",
                assertThrows(ApiException.class, () -> verifier.verify("signed.jwt")).getCode());
    }

    @Test void wrongIssuer_isRejected() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT,
                raw -> token(CLIENT, "https://evil.invalid", true, Instant.now().plusSeconds(300)));
        assertThrows(ApiException.class, () -> verifier.verify("signed.jwt"));
    }

    @Test void unverifiedEmail_isRejected() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT,
                raw -> token(CLIENT, "https://accounts.google.com", false, Instant.now().plusSeconds(300)));
        assertThrows(ApiException.class, () -> verifier.verify("signed.jwt"));
    }

    @Test void expiredToken_isRejected() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT,
                raw -> token(CLIENT, "https://accounts.google.com", true, Instant.now().minusSeconds(5)));
        assertThrows(ApiException.class, () -> verifier.verify("signed.jwt"));
    }

    @Test void invalidSignature_isRejected() {
        JwtDecoder decoder = raw -> { throw new JwtException("bad signature"); };
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT, decoder);
        assertThrows(ApiException.class, () -> verifier.verify("bad.jwt"));
    }

    @Test void missingClientId_failsClosed() {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier("",
                raw -> token(CLIENT, "https://accounts.google.com", true, Instant.now().plusSeconds(300)));
        ApiException ex = assertThrows(ApiException.class, () -> verifier.verify("any.jwt"));
        assertEquals(503, ex.getStatus().value());
    }
}
