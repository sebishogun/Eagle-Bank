package com.eaglebank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when rate limit is exceeded
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyRequestsException extends RuntimeException {
    
    public TooManyRequestsException() {
        super("Rate limit exceeded");
    }
    
    public TooManyRequestsException(String message) {
        super(message);
    }
    
    public TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
    }
}