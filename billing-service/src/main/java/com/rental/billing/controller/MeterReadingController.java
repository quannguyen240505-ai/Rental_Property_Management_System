package com.rental.billing.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rental.billing.dto.MeterReadingResponse;
import com.rental.billing.service.MeterReadingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.rental.billing.dto.MeterReadingRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/meter-readings")
public class MeterReadingController {

    private final MeterReadingService service;

    public MeterReadingController(MeterReadingService service) {
        this.service = service;
    }

    @GetMapping
    public List<MeterReadingResponse> findByRoomAndPeriod(
            @RequestParam("roomId") String roomId,
            @RequestParam("period") String period,
            Authentication authentication) {

        return service.findByRoomAndPeriod(
                roomId,
                period,
                authentication);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeterReadingResponse create(
            @Valid @RequestBody MeterReadingRequest request,
            Authentication authentication) {

        return service.create(request, authentication);
    }
}