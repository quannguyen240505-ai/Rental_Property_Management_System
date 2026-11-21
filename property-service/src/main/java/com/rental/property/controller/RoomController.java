package com.rental.property.controller;

import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.service.RoomService;
import com.rental.property.dto.request.UpdateRoomRequest;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @RequestBody CreateRoomRequest request
    ) {
        RoomResponse response = roomService.createRoom(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getRoomsByPropertyId(
            @RequestParam UUID propertyId
    ) {
        List<RoomResponse> rooms =
                roomService.getRoomsByPropertyId(propertyId);

        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @PathVariable UUID id
    ) {
        RoomResponse room = roomService.getRoomById(id);

        return ResponseEntity.ok(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable UUID id,
            @RequestBody UpdateRoomRequest request
    ) {
        RoomResponse response = roomService.updateRoom(id, request);

        return ResponseEntity.ok(response);
    }


}
