package com.rental.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rental.billing.entity.InvoiceLine;

public interface InvoiceLineRepository
        extends JpaRepository<InvoiceLine, String> {

    List<InvoiceLine> findByInvoice_IdOrderByIdAsc(
            String invoiceId);
}