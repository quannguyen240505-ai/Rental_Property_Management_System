package com.rental.rental.controller;

import com.rental.rental.dto.CreateRentalContractRequest;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.service.RentalContractService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rental-contracts")
public class RentalContractController {

    private final RentalContractService contractService;

    public RentalContractController(
            RentalContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    public ResponseEntity<RentalContractResponse> createDraft(
            @Valid @RequestBody CreateRentalContractRequest request) {

        RentalContractResponse response = contractService.createDraft(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RentalContractResponse> getById(
            @PathVariable UUID id) {

        RentalContractResponse response = contractService.getById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RentalContractResponse>> getAll() {

        List<RentalContractResponse> responses = contractService.getAll();

        return ResponseEntity.ok(responses);
    }
}
