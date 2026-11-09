package com.rental.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rental.billing.entity.UtilityReading;
import com.rental.billing.entity.UtilityType;

public interface UtilityReadingRepository
        extends JpaRepository<UtilityReading, String> {

    boolean existsByRoomIdAndBillingPeriodAndUtilityType(
            String roomId,
            String billingPeriod,
            UtilityType utilityType);

    Optional<UtilityReading>
            findFirstByRoomIdAndUtilityTypeAndBillingPeriodLessThanOrderByBillingPeriodDesc(
                    String roomId,
                    UtilityType utilityType,
                    String billingPeriod);

    List<UtilityReading> findByRoomIdAndBillingPeriodOrderByUtilityTypeAsc(
            String roomId,
            String billingPeriod);
    boolean existsByRoomIdAndUtilityTypeAndBillingPeriodGreaterThan(
            String roomId,
            UtilityType utilityType,
            String billingPeriod);
}