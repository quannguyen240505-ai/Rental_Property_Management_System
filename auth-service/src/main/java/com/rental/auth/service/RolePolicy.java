package com.rental.auth.service;

import com.rental.auth.enums.Role;
import com.rental.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RolePolicy {

	public void validate(Role current, Role next, String contractReference, boolean contractStatusConfirmed,
			boolean manualEnabled) {
		if (current == Role.OWNER || next == Role.OWNER) {
			throw invalid("OWNER role cannot be modified through the API");
		}

		if (current == next) {
			throw invalid("User already has the requested role");
		}

		boolean employment = (current == Role.CUSTOMER && next == Role.STAFF)
				|| (current == Role.STAFF && next == Role.CUSTOMER);

		if (employment) {
			return;
		}

		boolean tenantTransition = (current == Role.CUSTOMER && next == Role.TENANT)
				|| (current == Role.TENANT && next == Role.CUSTOMER);

		if (!tenantTransition) {
			throw invalid("Unsupported role transition");
		}

		if (!manualEnabled) {
			throw new ApiException(HttpStatus.CONFLICT, "RENTAL_INTEGRATION_REQUIRED",
					"Tenant role changes require Rental Service contract verification");
		}

		if (contractReference == null || contractReference.isBlank() || !contractStatusConfirmed) {
			throw invalid("Contract reference and explicit OWNER confirmation are required");
		}
	}

	private ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_TRANSITION", message);
	}
}
