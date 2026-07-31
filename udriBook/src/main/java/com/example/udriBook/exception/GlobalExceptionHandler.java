package com.example.udriBook.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.udriBook.util.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin(origins = "*")
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle custom application exceptions
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<?> handleCustom(CustomException ex) {
        log.error("CustomException occurred: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle OTP-specific exceptions — preserves the HTTP status set on the exception
     * (e.g. 400 Bad Request for invalid OTP, 429 Too Many Requests for rate limit)
     */
    @ExceptionHandler(OtpException.class)
    public ResponseEntity<?> handleOtpException(OtpException ex) {
        log.warn("OTP error [{}]: {}", ex.getStatus(), ex.getMessage());
        ApiResponse<?> response = ApiResponse.error(ex.getStatus().value(), ex.getMessage());
        return new ResponseEntity<>(response, ex.getStatus());
    }


    /**
     * Handle @Valid validation failures on request bodies.
     * Returns 400 with the first field error message so the client
     * knows exactly which field is invalid (e.g. "Phone number is required").
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errorMessage);
        ApiResponse<?> response = ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMessage);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle CustomerNotFoundException
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<?> handleCustomerNotFound(CustomerNotFoundException ex) {
        log.error("CustomerNotFoundException occurred: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle Optimistic Locking Failures (Concurrent Modifications)
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLockingFailure(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure (concurrent modification): {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.error(HttpStatus.CONFLICT.value(), "The record was updated concurrently. Please refresh and try again.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Handle Database Integrity Violations (e.g. Data too long, duplicate key)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database integrity violation: {}", ex.getMostSpecificCause().getMessage());
        
        String rootCause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String message = "Database error: could not complete the request.";
        if (rootCause != null && rootCause.contains("Data truncation")) {
            message = "Data truncation: " + rootCause;
        } else if (rootCause != null && rootCause.contains("Duplicate entry")) {
            message = "Record already exists.";
        }
        
        ApiResponse<?> response = ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        ApiResponse<?> response = ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred: " + ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
