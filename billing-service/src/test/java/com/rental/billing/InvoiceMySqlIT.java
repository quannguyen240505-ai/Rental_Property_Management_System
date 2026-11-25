package com.rental.billing;

import com.rental.billing.dto.InvoiceLineRequest;
import com.rental.billing.dto.InvoiceRequest;
import com.rental.billing.entity.InvoiceLineType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceMySqlIT extends MySqlIntegrationTestSupport {

    @Test
    void concurrentDuplicateInvoiceCreatesOnlyOneInvoiceAndLine() throws Exception {
        InvoiceRequest request = invoiceRequest("Integration test rent");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> first = workers.submit(() -> createStatus(request, ready, start));
        Future<Integer> second = workers.submit(() -> createStatus(request, ready, start));

        try {
            awaitSignal(ready);
        } finally {
            start.countDown();
        }

        List<Integer> results = new ArrayList<>(List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)));
        results.sort(Integer::compareTo);

        assertEquals(List.of(201, 409), results);
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM invoices WHERE property_id = ?", propertyId));
        assertEquals(1L, count("""
                SELECT COUNT(*) FROM invoice_lines l
                JOIN invoices i ON i.id = l.invoice_id
                WHERE i.property_id = ?
                """, propertyId));
    }

    @Test
    void mysqlCheckFailureRollsBackInvoiceAndAllLines() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String constraint = "billing_it_reject_" + suffix;
        String rejectedDescription = "billing-it-" + suffix;

        // The guard allows only the dedicated local test schema. This temporary
        // MySQL CHECK causes a real database failure after the invoice is flushed.
        jdbc.execute("ALTER TABLE invoice_lines ADD CONSTRAINT " + constraint
                + " CHECK (description <> '" + rejectedDescription + "')");
        try {
            DataIntegrityViolationException exception = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> invoices.create(invoiceRequest(rejectedDescription), owner));

            StringBuilder causes = new StringBuilder();
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                causes.append(cause.getMessage()).append('\n');
            }
            assertTrue(causes.toString().contains(constraint),
                    "Expected the deliberate MySQL CHECK violation");
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM invoices WHERE property_id = ?", propertyId));
            assertEquals(0L, count(
                    "SELECT COUNT(*) FROM invoice_lines WHERE description = ?",
                    rejectedDescription));
        } finally {
            jdbc.execute("ALTER TABLE invoice_lines DROP CHECK " + constraint);
        }
    }

    private int createStatus(
            InvoiceRequest request, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitSignal(start);
        try {
            invoices.create(request, owner);
            return 201;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private InvoiceRequest invoiceRequest(String description) {
        return new InvoiceRequest(
                UUID.fromString(propertyId), UUID.fromString(roomId),
                UUID.randomUUID(), UUID.randomUUID(), "2026-11",
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(14),
                List.of(new InvoiceLineRequest(
                        InvoiceLineType.RENT, description,
                        BigDecimal.ONE, 2500000L)));
    }
}
