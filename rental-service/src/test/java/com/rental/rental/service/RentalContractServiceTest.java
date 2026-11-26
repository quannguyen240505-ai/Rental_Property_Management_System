package com.rental.rental.service;

import com.rental.rental.dto.CreateRentalContractRequest;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.entity.ContractStatus;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.entity.TenantProfile;
import com.rental.rental.exception.ContractConflictException;
import com.rental.rental.exception.ResourceNotFoundException;
import com.rental.rental.repository.RentalContractRepository;
import com.rental.rental.repository.TenantProfileRepository;
import com.rental.rental.property.PropertyVerificationClient;
import com.rental.rental.access.RentalContractAccessService;
import com.rental.rental.property.RoomContractEligibilityResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RentalContractServiceTest {

    private RentalContractRepository contractRepository;
    private TenantProfileRepository tenantRepository;
    private PropertyVerificationClient propertyVerificationClient;
    private RentalContractService contractService;
    private RentalContractAccessService contractAccessService;

    private UUID roomId;
    private UUID tenantId;
    private CreateRentalContractRequest request;

    @BeforeEach
    void setUp() {
        contractRepository = mock(RentalContractRepository.class);
        tenantRepository = mock(TenantProfileRepository.class);
        propertyVerificationClient = mock(PropertyVerificationClient.class);
        contractAccessService = mock(RentalContractAccessService.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner-test-id",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
                )
        );

        contractService = new RentalContractService(
                contractRepository,
                tenantRepository,
                propertyVerificationClient,
                contractAccessService
        );

        roomId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        when(propertyVerificationClient.getContractEligibility(
                eq(roomId), eq("owner-test-id")
        )).thenReturn(new RoomContractEligibilityResponse(
                roomId,
                UUID.randomUUID(),
                "AVAILABLE",
                true,
                null,
                false,
                null,
                null,
                null
        ));

        request = new CreateRentalContractRequest(
                roomId,
                tenantId,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 12, 1),
                2_000_000L,
                2_000_000L
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    @Test
    void createDraft_success() {
        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(contractRepository.existsOverlappingContract(
                eq(roomId),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(List.of(ContractStatus.ACTIVE))
        )).thenReturn(false);

        when(contractRepository.save(any(RentalContract.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RentalContractResponse response =
                contractService.createDraft(request);

        ArgumentCaptor<RentalContract> captor =
                ArgumentCaptor.forClass(RentalContract.class);

        verify(contractRepository).save(captor.capture());

        RentalContract saved = captor.getValue();

        assertEquals(ContractStatus.DRAFT, saved.getStatus());
        assertEquals(roomId, saved.getRoomId());
        assertEquals(2_000_000L, saved.getRentAmount());
        assertEquals(2_000_000L, saved.getDepositRequired());
        assertEquals("DRAFT", response.status());
    }

    @Test
    void createDraft_tenantNotFound() {
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> contractService.createDraft(request)
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void createDraft_overlappingActiveContract() {
        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(contractRepository.existsOverlappingContract(
                eq(roomId),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(List.of(ContractStatus.ACTIVE))
        )).thenReturn(true);

        assertThrows(
                ContractConflictException.class,
                () -> contractService.createDraft(request)
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void getAll_emptyList() {
        when(contractRepository.findAll())
                .thenReturn(List.of());

        List<RentalContractResponse> result =
                contractService.getAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(contractRepository).findAll();
    }

    @Test
    void getById_success() {
        UUID contractId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");

        RentalContract contract = new RentalContract();
        contract.setRoomId(roomId);
        contract.setTenantProfile(tenant);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setStartDate(LocalDate.of(2026, 12, 1));
        contract.setEndDate(LocalDate.of(2027, 12, 1));
        contract.setRentAmount(2_000_000L);
        contract.setDepositRequired(2_000_000L);

        when(contractRepository.findById(contractId))
                .thenReturn(Optional.of(contract));

        RentalContractResponse result =
                contractService.getById(contractId);

        assertEquals(roomId, result.roomId());
        assertEquals("DRAFT", result.status());
        assertEquals(2_000_000L, result.rentAmount());
    }

    @Test
    void getById_notFound() {
        UUID contractId = UUID.randomUUID();

        when(contractRepository.findById(contractId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> contractService.getById(contractId)
        );
    }

    @Test
    void createDraft_invalidDates() {
        CreateRentalContractRequest invalidRequest =
                new CreateRentalContractRequest(
                        roomId,
                        tenantId,
                        LocalDate.of(2027, 12, 1),
                        LocalDate.of(2026, 12, 1),
                        2_000_000L,
                        2_000_000L
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> contractService.createDraft(invalidRequest)
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void createDraft_sameStartAndEndDate() {
        CreateRentalContractRequest invalidRequest =
                new CreateRentalContractRequest(
                        roomId,
                        tenantId,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 12, 1),
                        2_000_000L,
                        2_000_000L
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> contractService.createDraft(invalidRequest)
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }
    @Test
    void createDraft_propertyUnavailable_doesNotSave() {
        TenantProfile tenant = new TenantProfile();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));
        doThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Property verification unavailable"
        )).when(propertyVerificationClient)
                .verifyRoomAccess(roomId, "owner-test-id");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(request)
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode());
        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }
    @Test
    void createDraft_owner_verifiesCorrectRoomAndUser() {
        TenantProfile tenant = new TenantProfile();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));
        when(contractRepository.save(any(RentalContract.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        contractService.createDraft(request);
        verify(propertyVerificationClient)
                .verifyRoomAccess(roomId, "owner-test-id");
        verify(contractRepository)
                .save(any(RentalContract.class));
    }
    @Test
    void createDraft_unauthenticated_doesNotSave() {
        SecurityContextHolder.clearContext();
        TenantProfile tenant = new TenantProfile();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(request)
        );
        assertEquals(HttpStatus.UNAUTHORIZED,
                exception.getStatusCode());
        verifyNoInteractions(propertyVerificationClient);
        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }
    @Test
    void createDraft_staffForbidden_doesNotSave() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "staff-test-id",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
                )
        );
        TenantProfile tenant = new TenantProfile();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(request)
        );
        assertEquals(HttpStatus.FORBIDDEN,
                exception.getStatusCode());
        verifyNoInteractions(propertyVerificationClient);
        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void getById_staffWithoutPropertyAccess_isForbidden() {
        UUID contractId = UUID.randomUUID();

        RentalContract contract = new RentalContract();
        contract.setRoomId(roomId);

        when(contractRepository.findById(contractId))
                .thenReturn(Optional.of(contract));

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not assigned to this property"
        )).when(contractAccessService)
                .requireContractAccess(contract);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.getById(contractId)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verify(contractAccessService)
                .requireContractAccess(contract);
    }

    @Test
    void getById_authorizedUser_checksContractAccess() {
        UUID contractId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");

        RentalContract contract = new RentalContract();
        contract.setRoomId(roomId);
        contract.setTenantProfile(tenant);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setStartDate(LocalDate.of(2026, 12, 1));
        contract.setEndDate(LocalDate.of(2027, 12, 1));
        contract.setRentAmount(2_000_000L);
        contract.setDepositRequired(2_000_000L);

        when(contractRepository.findById(contractId))
                .thenReturn(Optional.of(contract));

        RentalContractResponse response =
                contractService.getById(contractId);

        assertEquals(roomId, response.roomId());

        verify(contractAccessService)
                .requireContractAccess(contract);
    }

    private RentalContract buildContract(UUID id, UUID roomId) {
        TenantProfile tenant = new TenantProfile();

        RentalContract contract = new RentalContract();

        // Thiết lập ID cho entity trong Unit Test
        ReflectionTestUtils.setField(contract, "id", id);

        contract.setRoomId(roomId);
        contract.setTenantProfile(tenant);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setStartDate(LocalDate.of(2026, 12, 1));
        contract.setEndDate(LocalDate.of(2027, 12, 1));
        contract.setRentAmount(2_000_000L);
        contract.setDepositRequired(2_000_000L);

        return contract;
    }


    @Test
    void getAll_staffOnlySeesAssignedPropertyContracts() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        RentalContract allowedContract =
                buildContract(allowedId, UUID.randomUUID());

        RentalContract deniedContract =
                buildContract(deniedId, UUID.randomUUID());

        when(contractRepository.findAll())
                .thenReturn(List.of(allowedContract, deniedContract));

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not assigned to this property"
        )).when(contractAccessService)
                .requireContractAccess(deniedContract);

        List<RentalContractResponse> result =
                contractService.getAll();

        assertEquals(1, result.size());
        assertEquals(allowedId, result.get(0).id());

        verify(contractAccessService)
                .requireContractAccess(allowedContract);

        verify(contractAccessService)
                .requireContractAccess(deniedContract);
    }

    @Test
    void getAll_propertyServiceUnavailable_failsClosed() {
        RentalContract contract =
                buildContract(UUID.randomUUID(), UUID.randomUUID());

        when(contractRepository.findAll())
                .thenReturn(List.of(contract));

        doThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Property Service unavailable"
        )).when(contractAccessService)
                .requireContractAccess(contract);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.getAll()
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
    }

    @Test
    void createDraft_reservedWithApprovedHold_success() {
        UUID holdId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");
        ReflectionTestUtils.setField(tenant, "userId", customerId);

        CreateRentalContractRequest reservedRequest =
                new CreateRentalContractRequest(
                        roomId,
                        tenantId,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2027, 12, 1),
                        2_000_000L,
                        2_000_000L,
                        holdId
                );

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(propertyVerificationClient.getContractEligibility(
                roomId, "owner-test-id"
        )).thenReturn(new RoomContractEligibilityResponse(
                roomId,
                UUID.randomUUID(),
                "RESERVED",
                true,
                null,
                true,
                holdId,
                "APPROVED",
                customerId
        ));

        when(contractRepository.save(any(RentalContract.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RentalContractResponse response =
                contractService.createDraft(reservedRequest);

        ArgumentCaptor<RentalContract> captor =
                ArgumentCaptor.forClass(RentalContract.class);

        verify(contractRepository).save(captor.capture());

        RentalContract saved = captor.getValue();

        assertEquals(ContractStatus.DRAFT, saved.getStatus());
        assertEquals(holdId, saved.getHoldId());
        assertEquals(holdId, response.holdId());
    }

    @Test
    void createDraft_reservedWithWrongHold_rejected() {
        UUID requestedHoldId = UUID.randomUUID();
        UUID approvedHoldId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");
        ReflectionTestUtils.setField(tenant, "userId", customerId);

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(propertyVerificationClient.getContractEligibility(
                roomId, "owner-test-id"
        )).thenReturn(new RoomContractEligibilityResponse(
                roomId,
                UUID.randomUUID(),
                "RESERVED",
                true,
                null,
                true,
                approvedHoldId,
                "APPROVED",
                customerId
        ));

        CreateRentalContractRequest wrongHoldRequest =
                new CreateRentalContractRequest(
                        roomId,
                        tenantId,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2027, 12, 1),
                        2_000_000L,
                        2_000_000L,
                        requestedHoldId
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(wrongHoldRequest)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void createDraft_reservedWithWrongCustomer_rejected() {
        UUID holdId = UUID.randomUUID();
        UUID tenantUserId = UUID.randomUUID();
        UUID holdCustomerId = UUID.randomUUID();

        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");
        ReflectionTestUtils.setField(tenant, "userId", tenantUserId);

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(propertyVerificationClient.getContractEligibility(
                roomId, "owner-test-id"
        )).thenReturn(new RoomContractEligibilityResponse(
                roomId,
                UUID.randomUUID(),
                "RESERVED",
                true,
                null,
                true,
                holdId,
                "APPROVED",
                holdCustomerId
        ));

        CreateRentalContractRequest reservedRequest =
                new CreateRentalContractRequest(
                        roomId,
                        tenantId,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2027, 12, 1),
                        2_000_000L,
                        2_000_000L,
                        holdId
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(reservedRequest)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void createDraft_reservedPending_rejected() {
        assertRoomIneligible("RESERVED_PENDING", false);
    }

    @Test
    void createDraft_occupied_rejected() {
        assertRoomIneligible("OCCUPIED", false);
    }

    @Test
    void createDraft_availableButNotEligible_rejected() {
        assertRoomIneligible("AVAILABLE", false);
    }

    private void assertRoomIneligible(
            String roomStatus,
            boolean eligible
    ) {
        TenantProfile tenant = new TenantProfile();
        tenant.setFullName("Nguyen Van A");

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(propertyVerificationClient.getContractEligibility(
                roomId, "owner-test-id"
        )).thenReturn(new RoomContractEligibilityResponse(
                roomId,
                UUID.randomUUID(),
                roomStatus,
                eligible,
                "Room is not eligible",
                false,
                null,
                null,
                null
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> contractService.createDraft(request)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(contractRepository, never())
                .save(any(RentalContract.class));
    }

    @Test
    void createDraft_reservedPendingEvenIfEligible_rejected() {
        assertRoomIneligible("RESERVED_PENDING", true);
    }

    @Test
    void createDraft_occupiedEvenIfEligible_rejected() {
        assertRoomIneligible("OCCUPIED", true);
    }

}
