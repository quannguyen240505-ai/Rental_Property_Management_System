package com.rental.auth.service;

import com.rental.auth.dto.response.IntrospectionResponse;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenIntrospectionService {

    private final JwtDecoder jwtDecoder;

    public TokenIntrospectionService(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Transactional(readOnly = true)
    public IntrospectionResponse introspect(String rawToken) {
        try {
            // Reuse the existing Auth JWT validation pipeline.
            Jwt jwt = jwtDecoder.decode(rawToken);

            List<String> roles = jwt.getClaimAsStringList("roles");

            if (roles == null || roles.size() != 1) {
                return IntrospectionResponse.inactive();
            }

            String authority = roles.get(0);

            if (authority == null || !authority.startsWith("ROLE_")) {
                return IntrospectionResponse.inactive();
            }

            String role = authority.substring("ROLE_".length());

            return new IntrospectionResponse(
                    true,
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("username"),
                    role,
                    jwt.getExpiresAt()
            );

        } catch (JwtException | IllegalArgumentException ex) {
            // Do not expose token contents or validation internals.
            return IntrospectionResponse.inactive();
        }
    }
}
