package com.rental.rental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ContractConflictException extends RuntimeException {

    public ContractConflictException(String message) {
        super(message);
    }
}
