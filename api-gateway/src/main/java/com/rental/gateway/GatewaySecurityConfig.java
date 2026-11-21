package com.rental.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewaySecurityConfig {

	/**
	 * Gateway gọi trực tiếp Auth Service để xác thực JWT.
	 *
	 * Không cần JWT_SECRET: chỉ Auth Service giữ khóa ký JWT.
	 */
	@Bean
	public ReactiveAuthenticationManager authenticationManager(WebClient.Builder webClientBuilder,
			@Value("${app.auth.base-url}") String authBaseUrl,
			@Value("${app.auth.internal-service-key}") String serviceKey) {

		if (serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("AUTH_INTERNAL_SERVICE_KEY must contain at least 32 UTF-8 bytes");
		}

		WebClient authClient = webClientBuilder.baseUrl(authBaseUrl).defaultHeader("X-Service-Key", serviceKey).build();

		return new AuthIntrospectionAuthenticationManager(authClient);
	}

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
			ReactiveAuthenticationManager authenticationManager,
			@Value("${app.cors.frontend-origin}") String frontendOrigin) {

		// Xác thực Bearer JWT thông qua Auth Introspection.
		AuthenticationWebFilter authenticationFilter = new AuthenticationWebFilter(authenticationManager);

		authenticationFilter.setServerAuthenticationConverter(new ServerBearerTokenAuthenticationConverter());

		// REST API stateless: không lưu authentication vào session.
		authenticationFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

		// Auth lỗi hạ tầng -> 503; JWT không hợp lệ -> 401.
		authenticationFilter.setAuthenticationFailureHandler((webFilterExchange, exception) -> {

			HttpStatus status = exception instanceof AuthenticationServiceException ? HttpStatus.SERVICE_UNAVAILABLE
					: HttpStatus.UNAUTHORIZED;

			var response = webFilterExchange.getExchange().getResponse();

			response.setStatusCode(status);
			return response.setComplete();
		});

		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(frontendOrigin));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();

		corsSource.registerCorsConfiguration("/**", cors);

		return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable)

				.cors(config -> config.configurationSource(corsSource))

				.authorizeExchange(exchanges -> exchanges

						// Tuyệt đối không công khai internal API.
						.pathMatchers("/internal", "/internal/**").denyAll()

						// CORS preflight.
						.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

						// Health checks.
						.pathMatchers("/actuator/health", "/actuator/info").permitAll()

						// Authentication public endpoints.
						.pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register",
								"/api/auth/forgot-password", "/api/auth/reset-password")
						.permitAll()

						// Public room listing and details.
						.pathMatchers(HttpMethod.GET, "/api/public/rooms", "/api/public/rooms/**").permitAll()

						// All other endpoints require valid JWT.
						.anyExchange().authenticated())

				// Missing JWT -> 401 (không redirect đến login page).
				.exceptionHandling(config -> config.authenticationEntryPoint((exchange, exception) -> {
					exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
					return exchange.getResponse().setComplete();
				}))

				.addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

				.build();
	}
}
