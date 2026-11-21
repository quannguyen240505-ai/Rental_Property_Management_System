package com.rental.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ModuleSmokeTest {
	@Test
	void moduleHasApplicationClass() {
		assertNotNull(AuthServiceApplication.class);
	}
}
