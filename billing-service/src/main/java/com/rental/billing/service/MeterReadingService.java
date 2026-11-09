package com.rental.billing.service;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import com.rental.billing.dto.MeterReadingResponse;
import com.rental.billing.repository.UtilityReadingRepository;
import com.rental.billing.dto.MeterReadingRequest;
import com.rental.billing.entity.UtilityReading;

@Service
public class MeterReadingService {

    private final UtilityReadingRepository repository;

    public MeterReadingService(UtilityReadingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MeterReadingResponse> findByRoomAndPeriod(
            String roomId,
            String period,
            Authentication authentication) {

        requireOwner(authentication);
        validateQuery(roomId, period);

        return repository
                .findByRoomIdAndBillingPeriodOrderByUtilityTypeAsc(
                        roomId, period)
                .stream()
                .map(MeterReadingResponse::fromEntity)
                .toList();
    }
    @Transactional
    public MeterReadingResponse create(
            MeterReadingRequest request,
            Authentication authentication) {

        requireOwner(authentication);
        validateQuery(request.roomId(), request.billingPeriod());

        String recordedBy = authentication.getName();

        try {
            UUID.fromString(recordedBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token phải có sub là UUID người dùng");
        }

        boolean duplicate =
                repository.existsByRoomIdAndBillingPeriodAndUtilityType(
                        request.roomId(),
                        request.billingPeriod(),
                        request.utilityType());

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Phòng đã có chỉ số loại này trong kỳ");
        }

        boolean hasLaterPeriod =
                repository
                        .existsByRoomIdAndUtilityTypeAndBillingPeriodGreaterThan(
                                request.roomId(),
                                request.utilityType(),
                                request.billingPeriod());

        if (hasLaterPeriod) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đã có kỳ mới hơn, không thể nhập lùi kỳ");
        }

        Long previousPeriodCurrentReading =
                repository
                        .findFirstByRoomIdAndUtilityTypeAndBillingPeriodLessThanOrderByBillingPeriodDesc(
                                request.roomId(),
                                request.utilityType(),
                                request.billingPeriod())
                        .map(UtilityReading::getCurrentReading)
                        .orElse(null);

        MeterReadingPolicy.validate(
                request, previousPeriodCurrentReading);

        UtilityReading reading = new UtilityReading(
                request.propertyId(),
                request.roomId(),
                request.billingPeriod(),
                request.utilityType(),
                request.previousReading(),
                request.currentReading(),
                request.meterReplaced(),
                request.meterReplacementReason(),
                request.evidenceUrl(),
                recordedBy);
        try {
            UtilityReading saved = repository.saveAndFlush(reading);
            return MeterReadingResponse.fromEntity(saved);

        } catch (DataIntegrityViolationException ex) {
            Throwable cause = ex;

            while (cause != null) {
                if (cause instanceof java.sql.SQLException sql
                        && sql.getErrorCode() == 1062
                        && sql.getMessage() != null
                        && sql.getMessage().contains(
                                "uk_reading_room_period_type")) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Phòng đã có chỉ số loại này trong kỳ",
                            ex);
                }

                cause = cause.getCause();
            }

            throw ex;
        }
    }
    private void requireOwner(Authentication authentication) {
        boolean owner = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                "ROLE_OWNER".equals(
                                        authority.getAuthority()));

        if (!owner) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "API này hiện chỉ cho phép OWNER");
        }
    }

    private void validateQuery(String roomId, String period) {
        try {
            UUID parsed = UUID.fromString(roomId);

            if (!parsed.toString().equalsIgnoreCase(roomId)) {
                throw new IllegalArgumentException();
            }

            if (!period.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                throw new IllegalArgumentException();
            }

            YearMonth.parse(period);

        } catch (IllegalArgumentException
                | DateTimeParseException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "roomId phải là UUID và period có dạng yyyy-MM");
        }
    }
}