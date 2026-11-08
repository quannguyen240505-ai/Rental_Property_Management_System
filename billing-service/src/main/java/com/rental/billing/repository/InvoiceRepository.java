package com.rental.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rental.billing.entity.Invoice;

public interface InvoiceRepository
        extends JpaRepository<Invoice, String> {

    boolean existsByContractIdAndBillingPeriod(
            String contractId,
            String billingPeriod);
}