package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;
import com.rental.property.dto.request.UpdateRoomRequest;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    public RoomService(
            RoomRepository roomRepository,
            PropertyRepository propertyRepository
    ) {
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {

        // Validate request
        if (request == null
                || request.propertyId() == null
                || request.code() == null
                || request.code().isBlank()
                || request.title() == null
                || request.title().isBlank()
                || request.monthlyRent() == null
                || request.monthlyRent() < 0
                || (request.areaM2() != null
                && request.areaM2().signum() <= 0)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid room information"
            );
        }

        // Check property exists
        Property property = propertyRepository
                .findById(request.propertyId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Property not found"
                        )
                );

        String roomCode = request.code().trim();

        // Check duplicate room code
        if (roomRepository.existsByProperty_IdAndCode(
                property.getId(), roomCode)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room code already exists in this property"
            );
        }

        // Create room
        Room room = new Room();

        room.setProperty(property);
        room.setCode(roomCode);
        room.setTitle(request.title().trim());
        room.setDescription(request.description());
        room.setFloorNumber(request.floorNumber());
        room.setAreaM2(request.areaM2());
        room.setMonthlyRent(request.monthlyRent());

        room.setStatus(RoomStatus.AVAILABLE);
        room.setPublished(false);

        // Save into MySQL
        Room savedRoom = roomRepository.saveAndFlush(room);

        return toResponse(savedRoom);
    }

    private RoomResponse toResponse(Room room) {

        return new RoomResponse(
                room.getId(),
                room.getProperty().getId(),
                room.getCode(),
                room.getTitle(),
                room.getDescription(),
                room.getFloorNumber(),
                room.getAreaM2(),
                room.getMonthlyRent(),
                room.getStatus(),
                room.getPublished(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );


    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByPropertyId(
            UUID propertyId
    ) {
        if (propertyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Property ID is required"
            );
        }

        if (!propertyRepository.existsById(propertyId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Property not found"
            );
        }

        return roomRepository
                .findByProperty_IdOrderByCodeAsc(propertyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(UUID id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

        return toResponse(room);
    }

    @Transactional
    public RoomResponse updateRoom(
            UUID id,
            UpdateRoomRequest request
    ) {
        // 1. Find existing room
        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

        // 2. Validate request
        if (request == null
                || request.code() == null
                || request.code().isBlank()
                || request.code().trim().length() > 30
                || request.title() == null
                || request.title().isBlank()
                || request.title().trim().length() > 150
                || request.monthlyRent() == null
                || request.monthlyRent() < 0
                || (request.areaM2() != null
                && (request.areaM2().signum() <= 0
                || request.areaM2().precision()
                - request.areaM2().scale() > 6
                || request.areaM2().scale() > 2))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid room information"
            );
        }

        String newCode = request.code().trim();

        // 3. Check duplicate code if changed
        if (!room.getCode().equals(newCode)
                && roomRepository.existsByProperty_IdAndCode(
                room.getProperty().getId(),
                newCode
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room code already exists"
            );
        }

        // 4. Update editable fields
        room.setCode(newCode);
        room.setTitle(request.title().trim());
        room.setDescription(request.description());
        room.setFloorNumber(request.floorNumber());
        room.setAreaM2(request.areaM2());
        room.setMonthlyRent(request.monthlyRent());

        // Do not modify status, published or property
        Room updatedRoom = roomRepository.saveAndFlush(room);

        return toResponse(updatedRoom);
    }



}
