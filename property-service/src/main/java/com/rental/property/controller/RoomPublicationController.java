package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.UpdateRoomPublicationRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomPublicationController {
    private final RoomService roomService;
    private final PropertyAccessService access;

    public RoomPublicationController(RoomService roomService, PropertyAccessService access) {
        this.roomService = roomService;
        this.access = access;
    }

    @PatchMapping("/{id}/publication")
    public ResponseEntity<RoomResponse> setPublication(
            @PathVariable UUID id, @RequestBody UpdateRoomPublicationRequest request,
            HttpServletRequest httpRequest) {
        // SRS: only OWNER can decide whether a room is public.
        access.requireOwner(httpRequest);
        if (request == null || request.published() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "published is required");
        }
        return ResponseEntity.ok(roomService.setRoomPublished(id, request.published()));
    }
}
