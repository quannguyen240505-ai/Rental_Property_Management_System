package com.rental.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rental.payment.dto.request.CreateDepositReceiptRequest;
import com.rental.payment.dto.response.DepositReceiptResponse;
import com.rental.payment.service.DepositReceiptService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/deposit-receipts")
public class DepositReceiptController {

    private final DepositReceiptService service;

    public DepositReceiptController(DepositReceiptService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepositReceiptResponse create(
            @Valid @RequestBody CreateDepositReceiptRequest request,
            Authentication authentication) {

        return service.create(request, authentication);
    }
}