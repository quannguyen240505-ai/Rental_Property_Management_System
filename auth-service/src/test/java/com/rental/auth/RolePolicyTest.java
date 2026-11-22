
package com.rental.auth;

import com.rental.auth.enums.Role;
import com.rental.auth.exception.ApiException;
import com.rental.auth.service.RolePolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePolicyTest {

	private final RolePolicy policy = new RolePolicy();

	@Test
	void ownerCanPromoteCustomerWithExplicitContractEvidenceInLocalMode() {
		assertDoesNotThrow(() -> policy.validate(Role.CUSTOMER, Role.TENANT, "contract-123", true, true));
	}

	@Test
	void tenantPromotionRequiresContractReference() {
		assertThrows(ApiException.class, () -> policy.validate(Role.CUSTOMER, Role.TENANT, null, true, true));
	}

	@Test
	void productionRequiresRentalIntegration() {
		assertThrows(ApiException.class, () -> policy.validate(Role.CUSTOMER, Role.TENANT, "c1", true, false));
	}

	@Test
	void neverGrantOwner() {
		assertThrows(ApiException.class, () -> policy.validate(Role.CUSTOMER, Role.OWNER, null, false, true));
	}

	@Test
	void staffTransitionsAllowed() {
		assertDoesNotThrow(() -> policy.validate(Role.CUSTOMER, Role.STAFF, null, false, false));
	}
}
