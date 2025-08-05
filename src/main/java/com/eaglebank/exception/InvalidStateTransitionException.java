package com.eaglebank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an invalid account status transition is attempted.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidStateTransitionException extends RuntimeException {
    
    public InvalidStateTransitionException(String message) {
        super(message);
    }
    
    public InvalidStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}