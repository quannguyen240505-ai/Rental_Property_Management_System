
package com.rental.auth;

import com.rental.auth.exception.ApiException;
import com.rental.auth.service.PasswordPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void acceptsOwnerDemoPassword() {
		assertDoesNotThrow(() -> policy.validate("Owner@12345"));
	}

	@Test
	void rejectsShortPassword() {
		assertThrows(ApiException.class, () -> policy.validate("abc123"));
	}

	@Test
	void rejectsNoDigits() {
		assertThrows(ApiException.class, () -> policy.validate("abcdefghijk"));
	}

	@Test
	void rejectsBcryptOverlongPassword() {
		assertThrows(ApiException.class, () -> policy.validate("Ab1" + "a".repeat(85)));
	}
}
