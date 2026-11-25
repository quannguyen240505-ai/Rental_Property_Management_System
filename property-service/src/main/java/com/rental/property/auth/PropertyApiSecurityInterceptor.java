package com.rental.property.auth;

import com.rental.property.auth.dto.TokenIntrospectionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** Auth Service alone validates JWT. VIEWINGS allow authenticated CUSTOMER/TENANT too. */
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
        String role = user.role();
        String path = request.getRequestURI();
        boolean viewingApi = "/api/viewings".equals(path)
                || (path != null && path.startsWith("/api/viewings/"));
        boolean roomHoldApi = "/api/room-holds".equals(path)
                || (path != null && path.startsWith("/api/room-holds/"));
        boolean allowed = "OWNER".equals(role) || "STAFF".equals(role)
                || ((viewingApi || roomHoldApi) && ("CUSTOMER".equals(role) || "TENANT".equals(role)));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    (viewingApi || roomHoldApi) ? "Role not permitted for customer-facing API" : "Only OWNER or STAFF can manage Property");
        }
        request.setAttribute("authenticatedUserId", user.userId());
        request.setAttribute("authenticatedRole", role);
        return true;
    }
}
