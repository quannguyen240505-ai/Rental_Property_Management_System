package com.rental.payment.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        String code = ex.getReason() == null ? "REQUEST_REJECTED" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code", code));
    }
}
