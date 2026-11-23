package com.rental.rental.repository;

import com.rental.rental.entity.CheckoutSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutSettlementRepository extends JpaRepository<CheckoutSettlement, UUID> {
    Optional<CheckoutSettlement> findByContract_Id(UUID contractId);
}
