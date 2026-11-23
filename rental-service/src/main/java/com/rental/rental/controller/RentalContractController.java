package com.rental.rental.controller;

import com.rental.rental.dto.CreateRentalContractRequest;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.service.RentalContractService;
import com.rental.rental.dto.DepositSummaryResponse;
import com.rental.rental.service.ContractDepositService;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.service.ContractActivationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
public class RentalContractController {

    private final RentalContractService contractService;
    private final ContractDepositService depositService;
    private final ContractActivationService activationService;

    public RentalContractController(
            RentalContractService contractService,
            ContractDepositService depositService,
            ContractActivationService activationService
    ) {
        this.contractService = contractService;
        this.depositService = depositService;
        this.activationService = activationService;
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

    @GetMapping("/{id}/deposit-summary")
    public ResponseEntity<DepositSummaryResponse> getDepositSummary(
            @PathVariable UUID id
    ) {
        DepositSummaryResponse response =
                depositService.getDepositSummary(id);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<RentalContractResponse> activate(
            @PathVariable UUID id
    ) {
        RentalContract contract = activationService.activate(id);

        RentalContractResponse response = contractService.getById(contract.getId());

        return ResponseEntity.ok(response);
    }
}
