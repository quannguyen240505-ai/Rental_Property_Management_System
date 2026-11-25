package com.rental.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.rental.billing.entity.Invoice;
import com.rental.billing.entity.InvoiceStatus;

class InvoiceIssueTest {

    private final LocalDate issueDate = LocalDate.of(2026, 11, 9);

    private Invoice draft(LocalDate dueDate) {
        return new Invoice(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "2026-11",
                dueDate,
                2250000L,
                UUID.randomUUID().toString());
    }

    @Test
    void issuesDraftAndPreservesAmounts() {
        Invoice invoice = draft(issueDate.plusDays(10));

        invoice.issue(issueDate);

        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertEquals(issueDate, invoice.getIssueDate());
        assertEquals(Long.valueOf(2250000L), invoice.getTotalAmount());
        assertEquals(Long.valueOf(0L), invoice.getPaidAmount());
    }

    @Test
    void rejectsRepeatedIssueWithoutChangingDate() {
        Invoice invoice = draft(issueDate.plusDays(10));
        invoice.issue(issueDate);

        assertThrows(IllegalStateException.class,
                () -> invoice.issue(issueDate.plusDays(1)));

        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertEquals(issueDate, invoice.getIssueDate());
    }

    @Test
    void rejectsDueDateBeforeIssueDate() {
        Invoice invoice = draft(issueDate.minusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> invoice.issue(issueDate));

        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertNull(invoice.getIssueDate());
    }

    @Test
    void allowsDueDateOnIssueDate() {
        Invoice invoice = draft(issueDate);

        invoice.issue(issueDate);

        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
    }

    @Test
    void rejectsMissingDueDate() {
        Invoice invoice = draft(null);

        assertThrows(IllegalArgumentException.class,
                () -> invoice.issue(issueDate));

        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertNull(invoice.getIssueDate());
    }

    @Test
    void rejectsMissingIssueDate() {
        Invoice invoice = draft(issueDate.plusDays(10));

        assertThrows(IllegalArgumentException.class,
                () -> invoice.issue(null));

        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertNull(invoice.getIssueDate());
    }
}