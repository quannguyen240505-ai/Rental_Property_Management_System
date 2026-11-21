package com.rental.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalServiceKeyFilter extends OncePerRequestFilter {

    private static final String INTROSPECT_PATH =
            "/internal/auth/introspect";

    private static final String SERVICE_KEY_HEADER =
            "X-Service-Key";

    private final byte[] expectedKey;

    public InternalServiceKeyFilter(String configuredKey) {
        byte[] keyBytes = configuredKey == null
                ? new byte[0]
                : configuredKey.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "AUTH_INTERNAL_SERVICE_KEY must be at least 32 UTF-8 bytes");
        }

        this.expectedKey = keyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && INTROSPECT_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String providedKey = request.getHeader(SERVICE_KEY_HEADER);

        byte[] providedBytes = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedKey, providedBytes)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            response.getWriter().write(
                    "{\"code\":\"INVALID_SERVICE_KEY\","
                    + "\"message\":\"Invalid internal service credentials\"}");

            return;
        }

        filterChain.doFilter(request, response);
    }
}
