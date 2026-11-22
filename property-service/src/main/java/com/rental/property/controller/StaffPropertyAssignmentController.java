package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.service.StaffPropertyAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties/{propertyId}/staff")
public class StaffPropertyAssignmentController {
    private final StaffPropertyAssignmentService service;
    private final PropertyAccessService access;

    public StaffPropertyAssignmentController(StaffPropertyAssignmentService service,
                                             PropertyAccessService access) {
        this.service = service;
        this.access = access;
    }

    public record StaffAssignmentResponse(UUID propertyId, UUID staffId) { }

    @PutMapping("/{staffId}")
    public ResponseEntity<StaffAssignmentResponse> assign(@PathVariable UUID propertyId,
            @PathVariable UUID staffId, HttpServletRequest request) {
        access.requireOwner(request);
        service.assign(propertyId, staffId, request.getHeader("Authorization"));
        return ResponseEntity.ok(new StaffAssignmentResponse(propertyId, staffId));
    }

    @DeleteMapping("/{staffId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID propertyId,
            @PathVariable UUID staffId, HttpServletRequest request) {
        access.requireOwner(request);
        service.revoke(propertyId, staffId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<UUID>> list(@PathVariable UUID propertyId,
            HttpServletRequest request) {
        access.requireOwner(request);
        return ResponseEntity.ok(service.getAssignedStaff(propertyId));
    }
}
