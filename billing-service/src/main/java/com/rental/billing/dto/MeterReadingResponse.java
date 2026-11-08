package com.rental.billing.dto;

import java.time.LocalDateTime;

import com.rental.billing.entity.UtilityReading;
import com.rental.billing.entity.UtilityType;

public record MeterReadingResponse(
        String id,
        String propertyId,
        String roomId,
        String billingPeriod,
        UtilityType utilityType,
        Long previousReading,
        Long currentReading,
        Long consumption,
        boolean meterReplaced,
        String meterReplacementReason,
        String evidenceUrl,
        String recordedBy,
        LocalDateTime recordedAt
) {

    public static MeterReadingResponse fromEntity(UtilityReading reading) {
        return new MeterReadingResponse(
                reading.getId(),
                reading.getPropertyId(),
                reading.getRoomId(),
                reading.getBillingPeriod(),
                reading.getUtilityType(),
                reading.getPreviousReading(),
                reading.getCurrentReading(),
                reading.getCurrentReading() - reading.getPreviousReading(),
                reading.isMeterReplaced(),
                reading.getMeterReplacementReason(),
                reading.getEvidenceUrl(),
                reading.getRecordedBy(),
                reading.getRecordedAt()
        );
    }
}