package com.rental.rental.controller;

import com.rental.rental.dto.CreateTenantProfileRequest;
import com.rental.rental.dto.TenantProfileResponse;
import com.rental.rental.service.TenantProfileService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenant-profiles")
public class TenantProfileController {

    private final TenantProfileService service;

    public TenantProfileController(TenantProfileService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TenantProfileResponse> create(
            @Valid @RequestBody CreateTenantProfileRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantProfileResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(service.getById(id));
    }
}
