package com.rental.rental.security;

import com.rental.rental.security.AuthIntrospectionClient.AuthUser;
import com.rental.rental.security.AuthIntrospectionClient.AuthServiceUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final AuthIntrospectionClient authClient;

    public BearerTokenFilter(AuthIntrospectionClient authClient) {
        this.authClient = authClient;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        // Không cần xác thực cho health check.
        String path = request.getServletPath();
        if (path.equals("/actuator/health")
                || path.equals("/actuator/info")) {
            chain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            sendError(response, 401, "UNAUTHORIZED", "Missing Bearer token");
            return;
        }

        String token = authorization.substring(7);

        try {
            AuthUser user = authClient.introspect(token);

            if (!user.active()
                    || user.userId() == null
                    || user.role() == null) {
                sendError(response, 401, "UNAUTHORIZED",
                        "Invalid or expired token");
                return;
            }

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.role())
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    user.userId(), null, authorities
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            chain.doFilter(request, response);

        } catch (AuthServiceUnavailableException ex) {
            sendError(response, 503, "AUTH_SERVICE_UNAVAILABLE",
                    "Authentication service unavailable");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + code
                        + "\",\"message\":\"" + message + "\"}"
        );
    }
}
