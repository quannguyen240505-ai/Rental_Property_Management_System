package com.rental.rental.access;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import com.rental.rental.entity.RentalContract;

import java.util.UUID;

@Service
public class RentalContractAccessService {

    private final RentalPropertyAccessClient propertyAccessClient;

    public RentalContractAccessService(
            RentalPropertyAccessClient propertyAccessClient) {
        this.propertyAccessClient = propertyAccessClient;
    }

    public void requireRoomAccess(UUID roomId) {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        boolean allowedRole = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_OWNER".equals(authority.getAuthority())
                                || "ROLE_STAFF".equals(authority.getAuthority())
                );

        if (!allowedRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "OWNER or STAFF permission required"
            );
        }

        if (roomId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room ID is required"
            );
        }

        propertyAccessClient.requireRoomAccess(
                roomId,
                authentication.getName()
        );
    }
    public void requireContractAccess(RentalContract contract) {
        if (contract == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Rental contract not found"
            );
        }

        requireRoomAccess(contract.getRoomId());
    }
}
