package com.rental.property.dto.request;

public record CreatePropertyRequest(
        String name,
        String address,
        String description,
        String contactPhone
) {
}
