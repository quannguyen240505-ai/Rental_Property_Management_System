package com.rental.auth.controller;

import com.rental.auth.dto.request.IntrospectionRequest;
import com.rental.auth.dto.response.IntrospectionResponse;
import com.rental.auth.service.TokenIntrospectionService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final TokenIntrospectionService introspectionService;

    public InternalAuthController(
            TokenIntrospectionService introspectionService) {

        this.introspectionService = introspectionService;
    }

    @PostMapping("/introspect")
    public IntrospectionResponse introspect(
            @Valid @RequestBody IntrospectionRequest request) {

        return introspectionService.introspect(request.token());
    }
}
