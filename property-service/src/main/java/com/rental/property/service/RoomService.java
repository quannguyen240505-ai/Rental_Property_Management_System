package com.rental.property.service;

import com.rental.property.dto.request.CreateRoomRequest;
import com.rental.property.dto.request.UpdateRoomRequest;
import com.rental.property.dto.response.PublicRoomResponse;
import com.rental.property.dto.response.RoomResponse;
import com.rental.property.entity.Property;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomStatus;
import com.rental.property.repository.PropertyRepository;
import com.rental.property.repository.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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

    // CREATE ROOM
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {

        if (request == null
                || request.propertyId() == null
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

        Property property = propertyRepository
                .findByIdForUpdate(request.propertyId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Property not found"
                        )
                );

        String roomCode = request.code().trim();

        if (roomRepository.existsByProperty_IdAndCode(
                property.getId(), roomCode)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room code already exists in this property"
            );
        }

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

        Room savedRoom = roomRepository.saveAndFlush(room);

        return toResponse(savedRoom);
    }

    // GET ROOMS BY PROPERTY ID
    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByPropertyId(UUID propertyId) {

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

    // GET ROOM BY ID
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

    // UPDATE ROOM: share the row-level lock with Room Hold and Viewing.
    @Transactional
    public RoomResponse updateRoom(
            UUID id,
            UpdateRoomRequest request
    ) {

        Room room = roomRepository.findForUpdateById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

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

    // PUBLISH OR UNPUBLISH ROOM: the availability check must run under the same lock.
    @Transactional
    public RoomResponse setRoomPublished(
            UUID id,
            boolean published
    ) {

        Room room = roomRepository.findForUpdateById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

        if (published
                && room.getStatus() != RoomStatus.AVAILABLE) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only available rooms can be published"
            );
        }

        room.setPublished(published);

        Room savedRoom = roomRepository.saveAndFlush(room);

        return toResponse(savedRoom);
    }

    // GET PUBLIC ROOMS
    @Transactional(readOnly = true)
    public List<PublicRoomResponse> getPublicRooms() {

        return roomRepository
                .findByPublishedTrueAndStatusOrderByCodeAsc(
                        RoomStatus.AVAILABLE
                )
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    // CONVERT ENTITY TO ADMIN RESPONSE
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

    // CONVERT ENTITY TO PUBLIC RESPONSE
    private PublicRoomResponse toPublicResponse(Room room) {

        Property property = room.getProperty();

        return new PublicRoomResponse(
                room.getId(),
                property.getId(),
                property.getName(),
                room.getCode(),
                room.getTitle(),
                room.getDescription(),
                room.getFloorNumber(),
                room.getAreaM2(),
                room.getMonthlyRent(),
                property.getContactPhone()
        );
    }

    @Transactional(readOnly = true)
    public PublicRoomResponse getPublicRoomById(UUID id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

        // Hidden or unavailable rooms are not visible to guests
        if (!Boolean.TRUE.equals(room.getPublished())
                || room.getStatus() != RoomStatus.AVAILABLE) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        return toPublicResponse(room);
    }

}
