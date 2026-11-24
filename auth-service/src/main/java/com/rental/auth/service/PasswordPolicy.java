package com.rental.auth.service;

import com.rental.auth.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

	public void validate(String password) {
		if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72
				|| !password.matches("(?s).*[A-Za-z].*") || !password.matches("(?s).*\\d.*")) {

			throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
					"Password must contain 10+ characters with letters and numbers (max 72 UTF-8 bytes)");
		}
	}
}
