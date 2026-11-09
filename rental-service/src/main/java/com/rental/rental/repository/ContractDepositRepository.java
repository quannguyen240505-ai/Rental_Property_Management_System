package com.rental.rental.repository;

import com.rental.rental.entity.ContractDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContractDepositRepository
        extends JpaRepository<ContractDeposit, UUID> {
}
