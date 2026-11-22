package com.rental.property.service;

import com.rental.property.dto.request.CreatePropertyRequest;
import com.rental.property.dto.response.PropertyResponse;
import com.rental.property.entity.Property;
import com.rental.property.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.address() == null || request.address().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Property name and address are required");
        }
        Property property = new Property();
        property.setName(request.name().trim());
        property.setAddress(request.address().trim());
        property.setDescription(request.description());
        property.setContactPhone(request.contactPhone());
        return toResponse(propertyRepository.saveAndFlush(property));
    }

    private PropertyResponse toResponse(Property property) {
        return new PropertyResponse(property.getId(), property.getName(),
                property.getAddress(), property.getDescription(), property.getContactPhone(),
                property.getCreatedAt(), property.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> getAllProperties() {
        return propertyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> getPropertiesByIds(List<UUID> ids) {
        return propertyRepository.findAllById(ids).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(UUID id) {
        Property property = propertyRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        return toResponse(property);
    }
}
