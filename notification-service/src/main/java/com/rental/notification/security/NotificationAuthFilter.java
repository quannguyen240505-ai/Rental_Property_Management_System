package com.rental.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class NotificationAuthFilter extends OncePerRequestFilter {
	private final AuthIntrospectionClient auth;

	public NotificationAuthFilter(AuthIntrospectionClient auth) {
		this.auth = auth;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
			reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Bearer token required");
			return;
		}
		try {
			AuthIntrospectionClient.Identity identity = auth.verify(authorization.substring(7).trim());
			if (identity == null) {
				reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Invalid or revoked token");
				return;
			}
			var principal = UsernamePasswordAuthenticationToken.authenticated(identity.userId().toString(), null,
					List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
			SecurityContextHolder.getContext().setAuthentication(principal);
			chain.doFilter(request, response);
		} catch (AuthIntrospectionClient.AuthUnavailableException e) {
			reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE",
					"Authentication service unavailable");
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
