package com.rental.rental.repository;

import com.rental.rental.entity.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RentalContractRepository
        extends JpaRepository<RentalContract, UUID> {
}
