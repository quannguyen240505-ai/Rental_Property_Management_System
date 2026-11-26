package com.rental.property.controller;

import com.rental.property.auth.PropertyAccessService;
import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.dto.request.UpdateRoomRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final PropertyAccessService access;

    public RoomController(RoomService roomService, PropertyAccessService access) {
        this.roomService = roomService;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @RequestBody CreateRoomRequest request, HttpServletRequest httpRequest) {
        access.requirePropertyAccess(httpRequest, request == null ? null : request.propertyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getRoomsByPropertyId(
            @RequestParam UUID propertyId, HttpServletRequest httpRequest) {
        access.requirePropertyAccess(httpRequest, propertyId);
        return ResponseEntity.ok(roomService.getRoomsByPropertyId(propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @PathVariable UUID id, HttpServletRequest httpRequest) {
        RoomResponse room = roomService.getRoomById(id);
        access.requirePropertyAccess(httpRequest, room.propertyId());
        return ResponseEntity.ok(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable UUID id, @RequestBody UpdateRoomRequest request,
            HttpServletRequest httpRequest) {
        RoomResponse existing = roomService.getRoomById(id);
        access.requirePropertyAccess(httpRequest, existing.propertyId());
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }
}
