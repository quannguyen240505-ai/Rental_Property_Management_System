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
    @Bean
    public ReactiveAuthenticationManager authenticationManager(WebClient.Builder webClientBuilder,
            @Value("${app.auth.base-url}") String authBaseUrl,
            @Value("${app.auth.internal-service-key}") String serviceKey) {
        if (serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AUTH_INTERNAL_SERVICE_KEY must contain at least 32 UTF-8 bytes");
        }
        WebClient authClient = webClientBuilder.baseUrl(authBaseUrl)
                .defaultHeader("X-Service-Key", serviceKey).build();
        return new AuthIntrospectionAuthenticationManager(authClient);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            ReactiveAuthenticationManager authenticationManager,
            @Value("${app.cors.frontend-origin}") String frontendOrigin) {
        AuthenticationWebFilter authenticationFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationFilter.setServerAuthenticationConverter(new ServerBearerTokenAuthenticationConverter());
        authenticationFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        authenticationFilter.setAuthenticationFailureHandler((exchange, ex) -> {
            HttpStatus status = ex instanceof AuthenticationServiceException ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.UNAUTHORIZED;
            var response = exchange.getExchange().getResponse();
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
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(config -> config.configurationSource(corsSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/internal", "/internal/**").denyAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/login", "/api/auth/register", "/api/auth/google",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/public/rooms", "/api/public/rooms/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(config -> config.authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }))
                .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
