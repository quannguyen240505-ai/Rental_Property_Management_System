package com.rental.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.UserStatus;
import com.rental.auth.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		return http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/info").permitAll()

						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register",
								"/api/auth/forgot-password", "/api/auth/reset-password")
						.permitAll()

						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter())))
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
	}

	@Bean
	public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.issuer}") String issuer,
			UserRepository users) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();

		OAuth2TokenValidator<Jwt> issuerAndTimes = JwtValidators.createDefaultWithIssuer(issuer);

		OAuth2TokenValidator<Jwt> accountState = token -> {
			try {
				UUID id = UUID.fromString(token.getSubject());

				Object rawVersion = token.getClaims().get("tv");

				if (!(rawVersion instanceof Number version)) {
					return invalidToken();
				}

				AppUser user = users.findById(id).orElse(null);

				if (user == null || user.getStatus() != UserStatus.ACTIVE
						|| user.getTokenVersion() != version.longValue()
						|| !List.of("ROLE_" + user.getRole().name()).equals(token.getClaimAsStringList("roles"))) {

					return invalidToken();
				}

				return OAuth2TokenValidatorResult.success();

			} catch (RuntimeException e) {
				return invalidToken();
			}
		};

		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTimes, accountState));

		return decoder;
	}

	private OAuth2TokenValidatorResult invalidToken() {
		return OAuth2TokenValidatorResult
				.failure(new OAuth2Error("invalid_token", "Account status or token version is no longer valid", null));
	}

	@Bean
	public JwtAuthenticationConverter authoritiesConverter() {
		JwtGrantedAuthoritiesConverter grants = new JwtGrantedAuthoritiesConverter();

		grants.setAuthoritiesClaimName("roles");
		grants.setAuthorityPrefix("");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

		converter.setJwtGrantedAuthoritiesConverter(grants);

		return converter;
	}

	private SecretKey key(String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 UTF-8 bytes");
		}

		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
