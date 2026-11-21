package com.rental.property.controller;

import com.rental.property.dto.request.CreatePropertyRequest;
import com.rental.property.dto.response.PropertyResponse;
import com.rental.property.service.PropertyService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(
            @RequestBody CreatePropertyRequest request
    ) {
        PropertyResponse response =
                propertyService.createProperty(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> getAllProperties() {

        List<PropertyResponse> properties =
                propertyService.getAllProperties();

        return ResponseEntity.ok(properties);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getPropertyById(
            @PathVariable UUID id
    ) {
        PropertyResponse property =
                propertyService.getPropertyById(id);

        return ResponseEntity.ok(property);
    }

}
