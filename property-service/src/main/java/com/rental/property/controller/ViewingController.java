package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreateViewingRequest;
import com.rental.property.dto.request.UpdateViewingResultRequest;
import com.rental.property.dto.response.ViewingResponse;
import com.rental.property.service.ViewingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Role and ownership checks use ONLY identities put on request by the Auth interceptor. */
@RestController
@RequestMapping("/api/viewings")
public class ViewingController {
    private final ViewingService service;
    private final PropertyAccessService access;

    public ViewingController(ViewingService service, PropertyAccessService access) {
        this.service = service;
        this.access = access;
    }

    private String role(HttpServletRequest request) {
        access.verifiedUserId(request); // rejects absent/unverified introspection identity
        return (String) request.getAttribute("authenticatedRole");
    }

    private void requireCustomer(HttpServletRequest request) {
        String r = role(request);
        if (!"CUSTOMER".equals(r) && !"TENANT".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER/TENANT required");
        }
    }

    private void requireManager(HttpServletRequest request) {
        String r = role(request);
        if (!"STAFF".equals(r) && !"OWNER".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER/STAFF required");
        }
    }

    @PostMapping
    public ResponseEntity<ViewingResponse> requestViewing(@RequestBody CreateViewingRequest body,
                                                          HttpServletRequest request) {
        requireCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(body, access.verifiedUserId(request)));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ViewingResponse>> mine(HttpServletRequest request) {
        requireCustomer(request);
        return ResponseEntity.ok(service.mine(access.verifiedUserId(request)));
    }

    @GetMapping("/available")
    public ResponseEntity<List<ViewingResponse>> available(HttpServletRequest request) {
        requireManager(request);
        boolean owner = access.isOwner(request);
        return ResponseEntity.ok(service.available(owner,
                owner ? List.of() : access.getVisiblePropertyIds(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ViewingResponse> details(@PathVariable UUID id, HttpServletRequest request) {
        ViewingResponse v = service.get(id);
        String r = role(request);
        if ("CUSTOMER".equals(r) || "TENANT".equals(r)) {
            if (!access.verifiedUserId(request).equals(v.customerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Viewing belongs to another user");
            }
        } else if ("STAFF".equals(r)) {
            access.requirePropertyAccess(request, v.propertyId());
        } else if (!"OWNER".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(v);
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ViewingResponse> claim(@PathVariable UUID id, HttpServletRequest request) {
        requireManager(request);
        ViewingResponse v = service.get(id);
        access.requirePropertyAccess(request, v.propertyId());
        return ResponseEntity.ok(service.claim(id, access.verifiedUserId(request),
                "STAFF".equals(role(request))));
    }

    @PatchMapping("/{id}/result")
    public ResponseEntity<ViewingResponse> result(@PathVariable UUID id,
            @RequestBody UpdateViewingResultRequest body, HttpServletRequest request) {
        requireManager(request);
        ViewingResponse v = service.get(id);
        access.requirePropertyAccess(request, v.propertyId());
        return ResponseEntity.ok(service.finish(id, access.verifiedUserId(request),
                access.isOwner(request), body == null ? null : body.result(),
                body == null ? null : body.notes()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ViewingResponse> cancel(@PathVariable UUID id,
                                                   HttpServletRequest request) {
        requireCustomer(request);
        return ResponseEntity.ok(service.cancel(id, access.verifiedUserId(request)));
    }
}
