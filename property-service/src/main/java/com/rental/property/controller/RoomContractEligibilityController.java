package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.response.ContractEligibilityResponse;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomContractEligibilityService;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A separate controller avoids changing RoomController's existing constructor. */
@RestController
@RequestMapping("/api/rooms")
public class RoomContractEligibilityController {
    private final RoomService roomService;
    private final PropertyAccessService access;
    private final RoomContractEligibilityService eligibility;

    public RoomContractEligibilityController(RoomService roomService,
            PropertyAccessService access, RoomContractEligibilityService eligibility) {
        this.roomService = roomService;
        this.access = access;
        this.eligibility = eligibility;
    }

    @GetMapping("/{roomId}/contract-eligibility")
    public ResponseEntity<ContractEligibilityResponse> getEligibility(
            @PathVariable UUID roomId, HttpServletRequest request) {
        // Resolve the property from the room itself; never trust a caller-supplied propertyId.
        RoomResponse room = roomService.getRoomById(roomId);
        access.requirePropertyAccess(request, room.propertyId());
        // Only authorized OWNER or assigned STAFF can inspect Hold/customer identity.
        return ResponseEntity.ok(eligibility.check(roomId));
    }
}
