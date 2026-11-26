package com.rental.auth;

import org.junit.jupiter.api.Test;
import com.rental.auth.enums.Role;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void shouldContainAllSystemRoles() {
        assertArrayEquals(
            new Role[] {
                Role.OWNER,
                Role.STAFF,
                Role.TENANT,
                Role.CUSTOMER
            },
            Role.values()
        );
    }
}
