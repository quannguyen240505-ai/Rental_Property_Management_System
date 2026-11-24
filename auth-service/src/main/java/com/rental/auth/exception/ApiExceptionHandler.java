package com.rental.auth.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status)
				.body(Map.of("code", code, "message", message, "timestamp", Instant.now().toString()));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Map<String, Object>> custom(ApiException e) {
		return error(e.getStatus(), e.getCode(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request fields");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON or enum value");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, Object>> duplicate(DataIntegrityViolationException e) {
		return error(HttpStatus.CONFLICT, "CONFLICT", "Duplicate or conflicting record");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
		return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient permissions");
	}
}
