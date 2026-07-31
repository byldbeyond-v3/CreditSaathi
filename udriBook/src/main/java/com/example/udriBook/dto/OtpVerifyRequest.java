package com.example.udriBook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for OTP verification.
 * Client sends the identifier (phone/email) and the 6-digit OTP.
 */
@Data
public class OtpVerifyRequest {

    @NotBlank(message = "Phone number or email is required")
    private String phoneOrEmail;

    @NotBlank(message = "OTP is required")
    private String otp;
}
