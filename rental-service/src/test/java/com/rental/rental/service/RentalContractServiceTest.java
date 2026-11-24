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

import org.junit.jupiter.api.BeforeEach;
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
    private RentalContractService contractService;

    private UUID roomId;
    private UUID tenantId;
    private CreateRentalContractRequest request;

    @BeforeEach
    void setUp() {
        contractRepository = mock(RentalContractRepository.class);
        tenantRepository = mock(TenantProfileRepository.class);

        contractService = new RentalContractService(
                contractRepository,
                tenantRepository
        );

        roomId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        request = new CreateRentalContractRequest(
                roomId,
                tenantId,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 12, 1),
                2_000_000L,
                2_000_000L
        );
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
}
