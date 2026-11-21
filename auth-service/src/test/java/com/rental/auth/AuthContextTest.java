package com.rental.auth;

import com.rental.auth.enums.Role;
import com.rental.auth.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthContextTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserRepository userRepository;

	@Test
	void shouldConnectToTestDatabase() {
		String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

		assertEquals("auth_test_db", database);
	}

	@Test
	void shouldInitializeOwnerAccount() {
		var owner = userRepository.findByUsernameIgnoreCase("owner").orElseThrow();

		assertEquals(Role.OWNER, owner.getRole());

		assertNotEquals("OwnerTest@12345", owner.getPasswordHash());
	}
}
