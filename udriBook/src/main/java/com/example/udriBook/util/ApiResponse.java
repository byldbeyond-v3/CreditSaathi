package com.example.udriBook.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ApiResponse - Unified API response wrapper for all REST endpoints
 * Provides consistent response format across the application
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;
    private String message;
    private Long totalCustomers;
    private T data;
    private List<String> errors;
    private Long timestamp;

    public ApiResponse(int status, String message, Long totalCustomers, T data) {
        this.status = status;
        this.message = message;
        this.totalCustomers = totalCustomers;
        this.data = data;
        this.errors = null; // optional
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success(int status, String message, Long totalCustomers, T data) {
        return new ApiResponse<>(status, message, totalCustomers, data);
    }

    /**
     * Build a success response
     * 
     * @param status  - HTTP status code
     * @param message - Success message
     * @param data    - Response data
     * @return - ApiResponse object
     */
    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Build an error response with single message
     * 
     * @param status  - HTTP status code
     * @param message - Error message
     * @return - ApiResponse object
     */
    public static ApiResponse<?> error(int status, String message) {
        return ApiResponse.builder()
                .status(status)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Build an error response with multiple error messages
     * 
     * @param status - HTTP status code
     * @param errors - List of error messages
     * @return - ApiResponse object
     */
    public static ApiResponse<?> error(int status, List<String> errors) {
        return ApiResponse.builder()
                .status(status)
                .message("Validation failed")
                .errors(errors)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
