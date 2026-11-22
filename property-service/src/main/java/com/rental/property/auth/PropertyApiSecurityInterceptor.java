package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** JWT is always verified by Auth; OWNER/STAFF data scope is checked in controllers. */
@Component
public class PropertyApiSecurityInterceptor implements HandlerInterceptor {
    private final AuthIntrospectionClient authClient;

    public PropertyApiSecurityInterceptor(AuthIntrospectionClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        TokenIntrospectionResponse user = authClient.introspectBearerToken(
                request.getHeader("Authorization"));
        if (!"OWNER".equals(user.role()) && !"STAFF".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only OWNER or STAFF can access management APIs");
        }
        request.setAttribute("authenticatedUserId", user.userId());
        request.setAttribute("authenticatedRole", user.role());
        return true;
    }
}
