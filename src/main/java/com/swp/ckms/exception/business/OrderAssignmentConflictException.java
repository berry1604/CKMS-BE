package com.swp.ckms.exception.business;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OrderAssignmentConflictException extends RuntimeException {
    public OrderAssignmentConflictException(String message) {
        super(message);
    }
}
