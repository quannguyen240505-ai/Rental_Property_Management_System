package com.rental.property.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PropertyApiSecurityConfig implements WebMvcConfigurer {

    private final PropertyApiSecurityInterceptor securityInterceptor;

    public PropertyApiSecurityConfig(PropertyApiSecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only protect business management API paths. Never intercept /api/public/**.
        registry.addInterceptor(securityInterceptor)
                .addPathPatterns(
                        "/api/properties", "/api/properties/**",
                        "/api/rooms", "/api/rooms/**"
                );
    }
}
