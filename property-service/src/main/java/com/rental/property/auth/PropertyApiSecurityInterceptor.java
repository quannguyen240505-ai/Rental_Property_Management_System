package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interim policy: OWNER-only management endpoints.
 * STAFF is denied until property-assignment checks are implemented.
 * The public /api/public/** routes are deliberately not intercepted.
 */
@Component
public class PropertyApiSecurityInterceptor implements HandlerInterceptor {

    private final AuthIntrospectionClient authClient;

    public PropertyApiSecurityInterceptor(AuthIntrospectionClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // CORS preflight is not an actual business request.
        // A matching CORS policy at Gateway / Property is still required for browsers.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        TokenIntrospectionResponse user = authClient.introspectBearerToken(
                request.getHeader("Authorization"));

        if (!"OWNER".equals(user.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This operation currently requires the OWNER role");
        }

        request.setAttribute("authenticatedUserId", user.userId());
        request.setAttribute("authenticatedRole", user.role());
        return true;
    }
}
