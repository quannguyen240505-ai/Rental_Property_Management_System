package com.rental.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.rental.billing.dto.MeterReadingRequest;

public final class MeterReadingPolicy {

    private MeterReadingPolicy() {
    }

    public static void validate(
            MeterReadingRequest request,
            Long previousPeriodCurrentReading) {

        if (request.previousReading() == null
                || request.currentReading() == null
                || request.previousReading() < 0
                || request.currentReading() < 0) {

            throw badRequest("Chỉ số phải là số không âm");
        }

        if (request.currentReading() < request.previousReading()) {
            throw badRequest(
                    "Chỉ số hiện tại không được nhỏ hơn chỉ số đầu kỳ");
        }

        if (request.meterReplaced()) {
            String reason = request.meterReplacementReason();

            if (reason == null || reason.isBlank()) {
                throw badRequest(
                        "Phải nhập lý do thay hoặc đặt lại công tơ");
            }
        } else if (previousPeriodCurrentReading != null
                && !request.previousReading()
                        .equals(previousPeriodCurrentReading)) {

            throw badRequest(
                    "Chỉ số đầu kỳ phải bằng chỉ số cuối kỳ trước: "
                            + previousPeriodCurrentReading);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message);
    }
}