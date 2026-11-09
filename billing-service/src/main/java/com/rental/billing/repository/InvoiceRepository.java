package com.rental.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rental.billing.entity.Invoice;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository
        extends JpaRepository<Invoice, String> {

    boolean existsByContractIdAndBillingPeriod(
            String contractId,
            String billingPeriod);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") String id);
}