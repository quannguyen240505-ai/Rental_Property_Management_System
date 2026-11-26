package com.rental.property.auth;

import com.rental.property.repository.StaffPropertyAssignmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Use ONLY identity attributes set by the verified Auth introspection interceptor. */
@Service
public class PropertyAccessService {
    private final StaffPropertyAssignmentRepository assignments;

    public PropertyAccessService(StaffPropertyAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    private String verifiedRole(HttpServletRequest request) {
        Object role = request.getAttribute("authenticatedRole");
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(role instanceof String) || !(userId instanceof UUID)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return (String) role;
    }

    public UUID verifiedUserId(HttpServletRequest request) {
        verifiedRole(request);
        return (UUID) request.getAttribute("authenticatedUserId");
    }

    public boolean isOwner(HttpServletRequest request) {
        return "OWNER".equals(verifiedRole(request));
    }

    public void requireOwner(HttpServletRequest request) {
        if (!isOwner(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER permission required");
        }
    }

    public void requirePropertyAccess(HttpServletRequest request, UUID propertyId) {
        String role = verifiedRole(request);
        if (propertyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "propertyId is required");
        }
        if ("OWNER".equals(role)) return;
        if ("STAFF".equals(role) && assignments.existsByStaffIdAndProperty_Id(
                verifiedUserId(request), propertyId)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Not assigned to this property");
    }

    @Transactional(readOnly = true)
    public List<UUID> getVisiblePropertyIds(HttpServletRequest request) {
        String role = verifiedRole(request);
        if (!"STAFF".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "STAFF permission required");
        }
        List<UUID> result = assignments.findByStaffId(verifiedUserId(request))
                .stream().map(x -> x.getProperty().getId()).toList();
        // Keep compatibility with the previous regression test, which expected
        // an unassigned STAFF account to receive 403 on management routes.
        if (result.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No property assignment found");
        }
        return result;
    }
}
