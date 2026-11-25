package com.rental.rental.repository;

import com.rental.rental.entity.ContractDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ContractDepositRepository
        extends JpaRepository<ContractDeposit, UUID> {

    boolean existsBySourceReceiptId(UUID sourceReceiptId);

    @Query("""
        SELECT COALESCE(SUM(d.appliedAmount), 0)
        FROM ContractDeposit d
        WHERE d.contract.id = :contractId
    """)
    Long sumAppliedAmountByContractId(
            @Param("contractId") UUID contractId
    );
}
