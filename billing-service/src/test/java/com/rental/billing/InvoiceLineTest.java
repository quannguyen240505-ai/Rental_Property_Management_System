package com.rental.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.rental.billing.entity.Invoice;
import com.rental.billing.entity.InvoiceLine;
import com.rental.billing.entity.InvoiceLineType;

class InvoiceLineTest {

    private Invoice invoice() {
        return new Invoice(
                "22222222-2222-4222-8222-222222222222",
                "11111111-1111-4111-8111-111111111111",
                "66666666-6666-4666-8666-666666666666",
                "55555555-5555-4555-8555-555555555555",
                "2026-11",
                LocalDate.of(2026, 11, 30),
                175000L,
                "11111111-1111-4111-8111-111111111111");
    }

    @Test
    void calculatesElectricityAmount() {
        var line = new InvoiceLine(
                invoice(), InvoiceLineType.ELECTRICITY,
                "Tiền điện", new BigDecimal("50"), 3500L);

        assertEquals(175000L, line.getAmount().longValue());
    }

    @Test
    void acceptsFractionalQuantityWithWholeVndAmount() {
        var line = new InvoiceLine(
                invoice(), InvoiceLineType.WATER,
                "Tiền nước", new BigDecimal("1.5"), 15000L);

        assertEquals(22500L, line.getAmount().longValue());
    }

    @Test
    void rejectsFractionalVndAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceLine(
                        invoice(), InvoiceLineType.OTHER,
                        "Khoản khác", new BigDecimal("0.001"), 1L));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceLine(
                        invoice(), InvoiceLineType.OTHER,
                        "Khoản khác", new BigDecimal("-1"), 1000L));
    }

    @Test
    void rejectsNegativeUnitPrice() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceLine(
                        invoice(), InvoiceLineType.OTHER,
                        "Khoản khác", BigDecimal.ONE, -1000L));
    }

    @Test
    void rejectsQuantityBeyondDatabasePrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceLine(
                        invoice(), InvoiceLineType.OTHER,
                        "Khoản khác",
                        new BigDecimal("1000000000"), 1L));
    }

    @Test
    void rejectsAmountOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceLine(
                        invoice(), InvoiceLineType.OTHER,
                        "Khoản khác",
                        new BigDecimal("2"), Long.MAX_VALUE));
    }
}