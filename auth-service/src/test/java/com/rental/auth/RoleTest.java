package com.rental.auth;

import org.junit.jupiter.api.Test;

import com.rental.auth.enums.Role;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
	@Test
	void onlyOwnerStaffTenantAreLoginRoles() {
		assertArrayEquals(new Role[] { Role.OWNER, Role.STAFF, Role.TENANT }, Role.values());
	}
}
