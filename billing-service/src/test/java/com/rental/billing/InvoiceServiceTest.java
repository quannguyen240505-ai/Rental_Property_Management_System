package com.rental.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.rental.billing.dto.InvoiceLineRequest;
import com.rental.billing.dto.InvoiceRequest;
import com.rental.billing.entity.Invoice;
import com.rental.billing.entity.InvoiceLineType;
import com.rental.billing.entity.InvoiceStatus;
import com.rental.billing.repository.InvoiceLineRepository;
import com.rental.billing.repository.InvoiceRepository;
import com.rental.billing.service.InvoiceService;

class InvoiceServiceTest {

    private InvoiceRepository invoiceRepository;
    private InvoiceLineRepository lineRepository;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        lineRepository = mock(InvoiceLineRepository.class);

        service = new InvoiceService(
                invoiceRepository, lineRepository);
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "11111111-1111-4111-8111-111111111111",
                null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private InvoiceRequest request() {
        return new InvoiceRequest(
                UUID.fromString(
                        "22222222-2222-4222-8222-222222222222"),
                UUID.fromString(
                        "11111111-1111-4111-8111-111111111111"),
                UUID.fromString(
                        "66666666-6666-4666-8666-666666666666"),
                UUID.fromString(
                        "55555555-5555-4555-8555-555555555555"),
                "2026-11",
                LocalDate.of(2026, 11, 30),
                List.of(
                        new InvoiceLineRequest(
                                InvoiceLineType.RENT,
                                "Tiền thuê",
                                BigDecimal.ONE,
                                2000000L),
                        new InvoiceLineRequest(
                                InvoiceLineType.ELECTRICITY,
                                "Tiền điện",
                                new BigDecimal("50"),
                                3500L),
                        new InvoiceLineRequest(
                                InvoiceLineType.WATER,
                                "Tiền nước",
                                new BigDecimal("5"),
                                15000L)));
    }

    @Test
    void ownerCreatesDraftWithCorrectTotal() {
        var response = service.create(
                request(), authentication("ROLE_OWNER"));

        assertEquals(InvoiceStatus.DRAFT, response.status());
        assertEquals(2250000L, response.totalAmount().longValue());
        assertEquals(0L, response.paidAmount().longValue());
        assertEquals(
                2250000L, response.outstandingAmount().longValue());
        assertEquals(3, response.lines().size());
        assertEquals(
                "11111111-1111-4111-8111-111111111111",
                response.createdBy());

        verify(invoiceRepository)
                .saveAndFlush(any(Invoice.class));
        verify(lineRepository).saveAllAndFlush(argThat(lines -> {
            int count = 0;
            long total = 0;

            for (var line : lines) {
                count++;
                total += line.getAmount();
                assertEquals(response.id(), line.getInvoice().getId());
            }

            return count == 3 && total == 2250000L;
        }));
    }

    @Test
    void rejectsDuplicateContractAndPeriodBeforeSaving() {
        when(invoiceRepository.existsByContractIdAndBillingPeriod(
                "66666666-6666-4666-8666-666666666666",
                "2026-11"))
                .thenReturn(true);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.create(
                        request(), authentication("ROLE_OWNER")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(invoiceRepository, never())
                .saveAndFlush(any(Invoice.class));
        verifyNoInteractions(lineRepository);
    }

    @Test
    void tenantCannotCreateInvoice() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.create(
                        request(), authentication("ROLE_TENANT")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());

        verifyNoInteractions(invoiceRepository, lineRepository);
    }
}