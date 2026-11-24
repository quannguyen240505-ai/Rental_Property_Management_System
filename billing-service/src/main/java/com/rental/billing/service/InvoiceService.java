package com.rental.billing.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rental.billing.dto.InvoiceRequest;
import com.rental.billing.dto.InvoiceResponse;
import com.rental.billing.entity.Invoice;
import com.rental.billing.entity.InvoiceLine;
import com.rental.billing.repository.InvoiceLineRepository;
import com.rental.billing.repository.InvoiceRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository lineRepository) {

        this.invoiceRepository = invoiceRepository;
        this.lineRepository = lineRepository;
    }

    @Transactional
    public InvoiceResponse create(
            InvoiceRequest request,
            Authentication authentication) {

        requireOwner(authentication);

        String createdBy = authentication.getName();

        try {
            UUID.fromString(createdBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token phải có sub là UUID người dùng");
        }

        String contractId = request.contractId().toString();

        if (invoiceRepository.existsByContractIdAndBillingPeriod(
                contractId, request.billingPeriod())) {

            throw duplicateInvoice();
        }

        Invoice invoice;
        List<InvoiceLine> lines;

        try {
            long totalAmount = 0;

            for (var line : request.lines()) {
                long amount = line.quantity()
                        .multiply(java.math.BigDecimal.valueOf(
                                line.unitPrice()))
                        .longValueExact();

                totalAmount = Math.addExact(totalAmount, amount);
            }

            invoice = new Invoice(
                    request.propertyId().toString(),
                    request.roomId().toString(),
                    contractId,
                    request.tenantUserId().toString(),
                    request.billingPeriod(),
                    request.dueDate(),
                    totalAmount,
                    createdBy);

            lines = request.lines().stream()
                    .map(line -> new InvoiceLine(
                            invoice,
                            line.lineType(),
                            line.description(),
                            line.quantity(),
                            line.unitPrice()))
                    .toList();

        } catch (ArithmeticException | IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản thu không hợp lệ: thành tiền phải là "
                    + "số nguyên VND và không vượt giới hạn lưu trữ",
                    ex);
        }

        try {
            invoiceRepository.saveAndFlush(invoice);
            lineRepository.saveAllAndFlush(lines);

        } catch (DataIntegrityViolationException ex) {
            Throwable cause = ex;

            while (cause != null) {
                if (cause instanceof SQLException sql
                        && sql.getErrorCode() == 1062
                        && sql.getMessage() != null
                        && sql.getMessage().contains(
                                "uk_invoice_contract_period")) {

                    throw duplicateInvoice();
                }

                cause = cause.getCause();
            }

            throw ex;
        }

        return InvoiceResponse.fromEntity(invoice, lines);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(
            UUID id,
            Authentication authentication) {

        requireOwner(authentication);

        Invoice invoice = invoiceRepository.findById(id.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hóa đơn"));

        List<InvoiceLine> lines =
                lineRepository.findByInvoice_IdOrderByIdAsc(
                        invoice.getId());

        return InvoiceResponse.fromEntity(invoice, lines);
    }
    @Transactional
    public InvoiceResponse issue(
            UUID id,
            Authentication authentication) {

        requireOwner(authentication);

        Invoice invoice = invoiceRepository
                .findByIdForUpdate(id.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hóa đơn"));

        List<InvoiceLine> lines =
                lineRepository.findByInvoice_IdOrderByIdAsc(
                        invoice.getId());

        if (lines.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hóa đơn phải có ít nhất một khoản thu");
        }

        try {
            invoice.issue(java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        invoiceRepository.flush();

        return InvoiceResponse.fromEntity(invoice, lines);
    }
    private void requireOwner(Authentication authentication) {
        boolean owner = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                "ROLE_OWNER".equals(
                                        authority.getAuthority()));

        if (!owner) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "API này hiện chỉ cho phép OWNER");
        }
    }

    private ResponseStatusException duplicateInvoice() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Hợp đồng đã có hóa đơn trong kỳ");
    }
}