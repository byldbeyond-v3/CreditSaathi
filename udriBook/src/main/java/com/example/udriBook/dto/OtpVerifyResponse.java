package com.example.udriBook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response DTO after successful OTP verification.
 * Contains the JWT access token and expiry info.
 */
@Data
@AllArgsConstructor
public class OtpVerifyResponse {

    /** JWT access token — client stores this securely and sends in every request */
    private String accessToken;

    /** Token type is always "Bearer" */
    private String tokenType;

    /** Message for display in the UI */
    private String message;

    /** User's phone or email — client may want to cache this */
    private String identifier;

    public static OtpVerifyResponse success(String token, String identifier) {
        return new OtpVerifyResponse(token, "Bearer", "OTP verified successfully", identifier);
    }
}
