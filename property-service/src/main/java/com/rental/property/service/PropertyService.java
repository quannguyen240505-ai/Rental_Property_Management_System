package com.rental.property.service;

import com.rental.property.entity.Property;
import com.rental.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Property createProperty(
            String name,
            String address,
            String description,
            String contactPhone
    ) {
        if (name == null || name.isBlank()
                || address == null || address.isBlank()) {
            throw new IllegalArgumentException(
                    "Property name and address are required"
            );
        }

        Property property = new Property();

        property.setName(name.trim());
        property.setAddress(address.trim());
        property.setDescription(description);
        property.setContactPhone(contactPhone);

        return propertyRepository.save(property);
    }
}
