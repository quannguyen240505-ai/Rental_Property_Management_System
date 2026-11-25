package com.rental.auth.service;

import com.rental.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/** Verifies Google ID tokens; never accepts a client-supplied email or user ID alone. */
@Component
public class GoogleIdTokenVerifier {
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build());
    }

    // Test seam: permits unit tests without contacting Google.
    GoogleIdTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    public record GoogleIdentity(String subject, String email, String fullName) {}

    public GoogleIdentity verify(String rawIdToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_LOGIN_NOT_CONFIGURED", "Google Login is not configured");
        }
        if (rawIdToken == null || rawIdToken.isBlank() || rawIdToken.length() > 8192) {
            throw invalid();
        }
        final Jwt jwt;
        try {
            // Nimbus verifies the RS256 signature using Google's public JWKs.
            jwt = decoder.decode(rawIdToken);
        } catch (JwtException ex) {
            throw invalid();
        }

        // Explicit checks in addition to cryptographic signature verification.
        String iss = jwt.getClaimAsString("iss");
        if (iss == null) iss = "";
        if (!"https://accounts.google.com".equals(iss)
                && !"accounts.google.com".equals(iss)) {
            throw invalid();
        }
        if (jwt.getAudience() == null || !jwt.getAudience().contains(clientId)) {
            throw invalid();
        }
        String azp = jwt.getClaimAsString("azp");
        if (azp != null && !clientId.equals(azp)) {
            throw invalid();
        }
        Instant expiry = jwt.getExpiresAt();
        if (expiry == null || !Instant.now().isBefore(expiry)
                || JwtValidators.createDefault().validate(jwt).hasErrors()) {
            throw invalid();
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw invalid();
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || subject.length() > 255
                || email == null || email.isBlank() || email.length() > 160) {
            throw invalid();
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw invalid();
        }
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = "Google User";
        }
        name = name.trim();
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        return new GoogleIdentity(subject, email, name);
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                "Invalid Google ID token");
    }
}
