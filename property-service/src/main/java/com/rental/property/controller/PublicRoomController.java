package com.rental.property.controller;

import com.rental.property.dto.response.PublicRoomResponse;
import com.rental.property.service.RoomService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {

    private final RoomService roomService;

    public PublicRoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<List<PublicRoomResponse>> getPublicRooms() {

        List<PublicRoomResponse> rooms =
                roomService.getPublicRooms();

        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicRoomResponse> getPublicRoomById(
            @PathVariable UUID id
    ) {
        PublicRoomResponse room =
                roomService.getPublicRoomById(id);

        return ResponseEntity.ok(room);
    }
}
