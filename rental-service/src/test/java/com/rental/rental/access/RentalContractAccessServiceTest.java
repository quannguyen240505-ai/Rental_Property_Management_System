package com.rental.rental.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rental.rental.entity.RentalContract;
import org.mockito.Mockito;

class RentalContractAccessServiceTest {

    @Mock
    private RentalPropertyAccessClient propertyAccessClient;

    private RentalContractAccessService accessService;
    private AutoCloseable mocks;

    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        accessService = new RentalContractAccessService(
                propertyAccessClient
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private void authenticate(String userId, String role) {
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @Test
    void ownerWithRoomAccessIsAllowed() {
        authenticate("owner-1", "OWNER");

        assertDoesNotThrow(() ->
                accessService.requireRoomAccess(roomId)
        );

        verify(propertyAccessClient)
                .requireRoomAccess(roomId, "owner-1");
    }

    @Test
    void staffWithRoomAccessIsAllowed() {
        authenticate("staff-1", "STAFF");

        assertDoesNotThrow(() ->
                accessService.requireRoomAccess(roomId)
        );

        verify(propertyAccessClient)
                .requireRoomAccess(roomId, "staff-1");
    }

    @Test
    void staffWithoutRoomAccessIsForbidden() {
        authenticate("staff-2", "STAFF");

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not assigned to this property"
        )).when(propertyAccessClient)
                .requireRoomAccess(roomId, "staff-2");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessService.requireRoomAccess(roomId)
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
    }

    @Test
    void tenantCannotAccessRoom() {
        authenticate("tenant-1", "TENANT");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessService.requireRoomAccess(roomId)
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(propertyAccessClient);
    }

    @Test
    void unauthenticatedUserIsRejected() {
        SecurityContextHolder.clearContext();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessService.requireRoomAccess(roomId)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(propertyAccessClient);
    }

    @Test
    void nullRoomIdIsRejected() {
        authenticate("staff-1", "STAFF");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> accessService.requireRoomAccess(null)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(propertyAccessClient);
    }

    @Test
    void staffCannotAccessContractFromAnotherProperty() {
        authenticate("staff-2", "STAFF");

        RentalContract contract = new RentalContract();
        contract.setRoomId(roomId);

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not assigned to this property"
        )).when(propertyAccessClient)
                .requireRoomAccess(roomId, "staff-2");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> accessService.requireContractAccess(contract)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verify(propertyAccessClient)
                .requireRoomAccess(roomId, "staff-2");
    }

    @Test
    void nullContractIsRejected() {
        authenticate("staff-1", "STAFF");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> accessService.requireContractAccess(null)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(propertyAccessClient);
    }

}
