package com.rental.property.controller;

import com.rental.property.dto.request.UpdateRoomPublicationRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Adds a publication endpoint without changing the team's existing
 * RoomController.java. Protected by PropertyApiSecurityInterceptor.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomPublicationController {

    private final RoomService roomService;

    public RoomPublicationController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PatchMapping("/{id}/publication")
    public ResponseEntity<RoomResponse> setPublication(
            @PathVariable UUID id,
            @RequestBody UpdateRoomPublicationRequest request
    ) {
        if (request == null || request.published() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "published is required"
            );
        }
        return ResponseEntity.ok(
                roomService.setRoomPublished(id, request.published())
        );
    }
}
