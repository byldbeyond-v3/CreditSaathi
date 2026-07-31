package com.example.udriBook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Dedicated exception for OTP-related errors.
 *
 * Carries an HTTP status so the GlobalExceptionHandler can return
 * accurate status codes (e.g. 429 for rate-limit, 400 for bad OTP).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OtpException extends RuntimeException {

    private final HttpStatus status;

    public OtpException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public OtpException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
