package com.example.udriBook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for phone OTP send/verify endpoints.
 * Used by /api/auth/phone/send-otp and /api/auth/phone/verify-otp.
 *
 * Validation annotations prevent obviously bad input from reaching the service
 * layer.
 */
public class PhoneOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\+?[0-9]{10,13}", message = "Enter a valid mobile number")
    private String phone;

    /**
     * OTP field — only required for the verify endpoint.
     * Optional here because the same DTO is used for both send (no OTP) and verify
     * (with OTP).
     */
    @Size(min = 4, max = 6, message = "OTP must be 4-6 digits")
    @Pattern(regexp = "[0-9]*", message = "OTP must contain digits only")
    private String otp;

    // ─── Getters & Setters ─────────────────────────────────────────

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
