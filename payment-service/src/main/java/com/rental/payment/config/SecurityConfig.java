package com.rental.payment.config;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public OpaqueTokenIntrospector tokenIntrospector(
            @Value("${auth.base-url:http://localhost:8081}") String baseUrl,
            @Value("${auth.service-key}") String serviceKey) {

        return new AuthTokenIntrospector(baseUrl, serviceKey);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OpaqueTokenIntrospector introspector) throws Exception {

        AuthenticationEntryPoint unauthorized =
                (request, response, exception) -> {
                    response.setHeader("WWW-Authenticate", "Bearer");
                    writeError(response, 401, "UNAUTHORIZED");
                };

        var provider = new OpaqueTokenAuthenticationProvider(introspector);
        var manager = new ProviderManager(provider);
        var bearerFilter = new BearerTokenAuthenticationFilter(manager);

        bearerFilter.setAuthenticationEntryPoint(unauthorized);
        bearerFilter.setAuthenticationFailureHandler(
                (request, response, exception) -> {
                    if (exception instanceof AuthenticationServiceException) {
                        writeError(response, 503, "AUTH_SERVICE_UNAVAILABLE");
                    } else {
                        unauthorized.commence(request, response, exception);
                    }
                });

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, 403, "FORBIDDEN")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/demo", "/demo/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/deposit-receipts/*/verify")
                        .hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/deposit-receipts/*/rental-eligibility")
                        .hasAnyRole("STAFF", "OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-receipts")
                        .hasAnyRole("STAFF", "OWNER")
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(
                        bearerFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    private static void writeError(
            HttpServletResponse response,
            int status,
            String code) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
