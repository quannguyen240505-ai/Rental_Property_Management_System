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
                        // Health check không yêu cầu JWT
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Tenant Profile: chỉ OWNER
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/tenant-profiles"
                        ).hasRole("OWNER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tenant-profiles/**"
                        ).hasRole("OWNER")

                        // Tạo hợp đồng: OWNER hoặc STAFF
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/contracts"
                        ).hasAnyRole("OWNER", "STAFF")

                        // Danh sách hợp đồng: chỉ OWNER
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/contracts"
                        ).hasRole("OWNER")

                        // Chi tiết hợp đồng: OWNER hoặc STAFF
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/contracts/**"
                        ).hasAnyRole("OWNER", "STAFF")

                        // Các API khác mặc định bị từ chối
                        .anyRequest().denyAll()
                )

                .addFilterBefore(
                        bearerTokenFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    // Tránh đăng ký BearerTokenFilter hai lần
    @Bean
    public FilterRegistrationBean<BearerTokenFilter>
    bearerTokenFilterRegistration(BearerTokenFilter filter) {

        FilterRegistrationBean<BearerTokenFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }
}
