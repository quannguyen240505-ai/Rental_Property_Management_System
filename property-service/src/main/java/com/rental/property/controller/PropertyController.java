package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreatePropertyRequest;
import com.rental.property.dto.response.PropertyResponse;
import com.rental.property.service.PropertyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {
    private final PropertyService propertyService;
    private final PropertyAccessService access;

    public PropertyController(PropertyService propertyService, PropertyAccessService access) {
        this.propertyService = propertyService;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(
            @RequestBody CreatePropertyRequest request, HttpServletRequest httpRequest) {
        // STAFF cannot create an unassigned property and grant access to itself.
        access.requireOwner(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.createProperty(request));
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> getAllProperties(HttpServletRequest httpRequest) {
        if (access.isOwner(httpRequest)) {
            return ResponseEntity.ok(propertyService.getAllProperties());
        }
        return ResponseEntity.ok(propertyService.getPropertiesByIds(
                access.getVisiblePropertyIds(httpRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getPropertyById(
            @PathVariable UUID id, HttpServletRequest httpRequest) {
        access.requirePropertyAccess(httpRequest, id);
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }
}
