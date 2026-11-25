package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CloseRoomHoldRequest;
import com.rental.property.dto.request.ConfirmRoomHoldRequest;
import com.rental.property.dto.request.CreateRoomHoldRequest;
import com.rental.property.dto.response.RoomHoldResponse;
import com.rental.property.dto.response.DepositEligibilityResponse;
import com.rental.property.service.RoomHoldService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Role and ownership checks rely on verified Auth interceptor attributes. */
@RestController
@RequestMapping("/api/room-holds")
public class RoomHoldController {
    private final RoomHoldService holds;
    private final PropertyAccessService access;

    public RoomHoldController(RoomHoldService holds, PropertyAccessService access) {
        this.holds = holds;
        this.access = access;
    }

    private String role(HttpServletRequest request) {
        access.verifiedUserId(request);
        return (String) request.getAttribute("authenticatedRole");
    }

    private void manager(HttpServletRequest request) {
        String r = role(request);
        if (!"OWNER".equals(r) && !"STAFF".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER/STAFF required");
        }
    }

    @PostMapping
    public ResponseEntity<RoomHoldResponse> create(@RequestBody CreateRoomHoldRequest req,
                                                   HttpServletRequest request) {
        manager(request);
        if (req == null || req.viewingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewingId required");
        }
        UUID propertyId = holds.viewingPropertyId(req.viewingId());
        access.requirePropertyAccess(request, propertyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(holds.create(req,
                access.verifiedUserId(request), "STAFF".equals(role(request))));
    }

    @PostMapping("/{id}/approve")
    public RoomHoldResponse approve(@PathVariable UUID id,
                                    @RequestBody ConfirmRoomHoldRequest req,
                                    HttpServletRequest request) {
        access.requireOwner(request);
        return holds.approve(id, access.verifiedUserId(request),
                req == null ? null : req.verificationReference());
    }

    @PostMapping("/{id}/cancel")
    public RoomHoldResponse cancel(@PathVariable UUID id, @RequestBody CloseRoomHoldRequest req,
                                   HttpServletRequest request) {
        access.requireOwner(request);
        return holds.cancel(id, access.verifiedUserId(request),
                req == null ? null : req.depositResolutionReference());
    }

    @PostMapping("/{id}/expire")
    public RoomHoldResponse expire(@PathVariable UUID id, @RequestBody CloseRoomHoldRequest req,
                                   HttpServletRequest request) {
        access.requireOwner(request);
        return holds.expire(id, access.verifiedUserId(request),
                req == null ? null : req.depositResolutionReference());
    }

    /**
     * Read-only eligibility, NOT a statement that any deposit is paid.
     * Payment must forward a verified user JWT until service-to-service auth
     * is specified and implemented by the team.
     */
    @GetMapping("/{id}/deposit-eligibility")
    public DepositEligibilityResponse depositEligibility(@PathVariable UUID id,
                                                         HttpServletRequest request) {
        // Payment forwards the original user Bearer JWT. Auth introspection
        // already ran in PropertyApiSecurityInterceptor (401/503 fail closed).
        // CUSTOMER/TENANT must not use this collection endpoint, even for
        // their own hold. Never use X-Service-Key as a user identity.
        manager(request);
        DepositEligibilityResponse eligibility = holds.depositEligibility(id);
        if ("STAFF".equals(role(request))) {
            access.requirePropertyAccess(request, eligibility.propertyId());
        }
        return eligibility;
    }

    @GetMapping("/{id}")
    public RoomHoldResponse get(@PathVariable UUID id, HttpServletRequest request) {
        RoomHoldResponse h = holds.get(id);
        String r = role(request);
        if ("OWNER".equals(r)) return h;
        if ("STAFF".equals(r)) {
            access.requirePropertyAccess(request, h.propertyId());
            return h;
        }
        if (("CUSTOMER".equals(r) || "TENANT".equals(r))
                && access.verifiedUserId(request).equals(h.customerId())) return h;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view this hold");
    }

    @GetMapping
    public List<RoomHoldResponse> byProperty(@RequestParam UUID propertyId,
                                             HttpServletRequest request) {
        manager(request);
        access.requirePropertyAccess(request, propertyId);
        return holds.byProperty(propertyId);
    }

    @GetMapping("/my")
    public List<RoomHoldResponse> mine(HttpServletRequest request) {
        String r = role(request);
        if (!"CUSTOMER".equals(r) && !"TENANT".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER/TENANT required");
        }
        return holds.mine(access.verifiedUserId(request));
    }
}
