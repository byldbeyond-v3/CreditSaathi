package com.example.udriBook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for OTP generation.
 * Client sends either a phone number or email address.
 */
@Data
public class GenerateOtpRequest {

    @NotBlank(message = "Phone number or email is required")
    private String phoneOrEmail;
}
