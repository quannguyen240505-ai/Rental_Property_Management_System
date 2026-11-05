package com.rental.property.repository;

import com.rental.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PropertyRepository
        extends JpaRepository<Property, UUID> {
}
