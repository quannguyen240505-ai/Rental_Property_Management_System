
package com.rental.auth.bootstrap;

import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import com.rental.auth.repository.UserRepository;
import com.rental.auth.service.PasswordPolicy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class OwnerSeeder {

	@Bean
	CommandLineRunner seedOwner(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,

			@Value("${app.bootstrap.owner.username:owner}") String username,

			@Value("${app.bootstrap.owner.password:}") String password,

			@Value("${app.bootstrap.owner.email:}") String email) {
		return args -> {

			if (users.countByRole(Role.OWNER) > 0) {
				return;
			}

			if (password == null || password.isBlank()) {
				throw new IllegalStateException("OWNER_BOOTSTRAP_PASSWORD is required when the OWNER does not exist");
			}

			policy.validate(password);

			if (users.existsByUsernameIgnoreCase(username)) {
				throw new IllegalStateException("OWNER bootstrap username is already used");
			}

			users.save(new AppUser(username.trim().toLowerCase(), encoder.encode(password), "System Owner",
					email == null || email.isBlank() ? null : email.trim().toLowerCase(), null, Role.OWNER));
		};
	}
}
