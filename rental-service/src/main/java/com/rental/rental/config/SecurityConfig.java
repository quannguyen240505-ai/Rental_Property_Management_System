package com.rental.rental.config;

import com.rental.rental.security.BearerTokenFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenFilter bearerTokenFilter
    ) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // Health check does not require JWT
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Tenant Profile: OWNER only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/tenant-profiles"
                        ).hasRole("OWNER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tenant-profiles/**"
                        ).hasRole("OWNER")

                        // Create contract: OWNER only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/contracts"
                        ).hasRole("OWNER")

                        // List contracts: OWNER only
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/contracts"
                        ).hasRole("OWNER")

                        // Settlement: OWNER only
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/contracts/*/settlement"
                        ).hasRole("OWNER")

                        // Contract details: OWNER only (temporary)
                        .requestMatchers(
                                 HttpMethod.GET,
                                 "/api/contracts/**"
                        ).hasRole("OWNER")

                        // Activate contract: OWNER only
                        .requestMatchers(
                                 HttpMethod.POST,
                                 "/api/contracts/*/activate"
                        ).hasRole("OWNER")

                        // Checkout and settlement: OWNER only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/contracts/*/checkout"
                        ).hasRole("OWNER")

                // Deny all other API requests
                        .anyRequest().denyAll()
                )

                .addFilterBefore(
                        bearerTokenFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    // Prevent duplicate BearerTokenFilter registration
    @Bean
    public FilterRegistrationBean<BearerTokenFilter>
    bearerTokenFilterRegistration(BearerTokenFilter filter) {

        FilterRegistrationBean<BearerTokenFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }
}
