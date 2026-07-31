package com.example.udriBook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.example.udriBook.dto.LoginRequest;
import com.example.udriBook.dto.OtpRequest;
import com.example.udriBook.dto.PhoneOtpRequest;
import com.example.udriBook.dto.RegisterRequest;
import com.example.udriBook.service.AuthService;
import com.example.udriBook.service.PhoneVerificationService;
import com.example.udriBook.dto.ResetPasswordRequest;
import com.example.udriBook.util.ApiResponse;
import jakarta.validation.Valid;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return buildSuccessResponse(HttpStatus.CREATED.value(),
                "User registered successfully", null);
    }

    @PostMapping("/login/password")
    public ResponseEntity<?> loginPassword(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        log.info("Login attempt with phone/email: {}", loginRequest.getPhoneOrEmail());
        String deviceInfo = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();
        Object loginResponse = authService.loginWithPassword(loginRequest, deviceInfo, ipAddress);

        return buildSuccessResponse(HttpStatus.OK.value(),
                "Login successful", loginResponse);
    }

    @PostMapping("/login/otp/send")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest otpRequest) {
        log.info("Sending OTP to: {}", otpRequest.getPhoneOrEmail());
        String result = authService.sendOtp(otpRequest.getPhoneOrEmail());

        return buildSuccessResponse(HttpStatus.OK.value(),
                "OTP sent successfully", result);
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<?> verifyOtp(
            @RequestParam String phoneOrEmail,
            @RequestParam String otp,
            HttpServletRequest request) {
        log.info("Verifying OTP for: {}", phoneOrEmail);
        String deviceInfo = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();
        Object loginResponse = authService.verifyOtp(phoneOrEmail, otp, deviceInfo, ipAddress);

        return buildSuccessResponse(HttpStatus.OK.value(),
                "Login successful", loginResponse);
    }

    /**
     * POST /api/auth/refresh — Get a new access token using a refresh token.
     *
     * Client should call this when the access token expires (401 response).
     * Send the refresh token in the request body.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Refresh token is required");
        }

        Object tokenResponse = authService.refreshAccessToken(refreshToken);
        return buildSuccessResponse(HttpStatus.OK.value(),
                "Token refreshed successfully", tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        if (body != null && body.containsKey("refreshToken")) {
            // Preferred: logout with both tokens
            authService.logoutWithTokens(token, body.get("refreshToken"));
        } else {
            // Backward compatible: logout with access token only
            authService.logout(token);
        }
        return buildSuccessResponse(HttpStatus.OK.value(),
                "Logged out successfully", null);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@RequestHeader("Authorization") String token) {
        Object sessions = authService.getActiveSessions(token);
        return buildSuccessResponse(HttpStatus.OK.value(),
                "Sessions retrieved successfully", sessions);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestHeader("Authorization") String token) {
        authService.logoutAll(token);
        return buildSuccessResponse(HttpStatus.OK.value(),
                "Logged out from other devices successfully", null);
    }

    @PostMapping("/logout-device/{sessionId}")
    public ResponseEntity<?> logoutDevice(
            @RequestHeader("Authorization") String token,
            @PathVariable Long sessionId) {
        authService.logoutDevice(token, sessionId);
        return buildSuccessResponse(HttpStatus.OK.value(),
                "Logged out from device successfully", null);
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<?> forgotPasswordRequest(@RequestBody OtpRequest otpRequest) {
        log.info("Forgot password request for: {}", otpRequest.getPhoneOrEmail());
        String result = authService.sendOtp(otpRequest.getPhoneOrEmail());
        return buildSuccessResponse(HttpStatus.OK.value(), "OTP sent successfully", result);
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<?> verifyResetOtp(@RequestParam String phoneOrEmail, @RequestParam String otp) {
        boolean isValid = authService.verifyResetOtp(phoneOrEmail, otp);
        if (isValid) {
            return buildSuccessResponse(HttpStatus.OK.value(), "OTP verified successfully", null);
        } else {
            return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid or expired OTP");
        }
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetRequest) {
        log.info("Password reset attempt for: {}", resetRequest.getPhoneOrEmail());
        authService.resetPassword(resetRequest.getPhoneOrEmail(),
                resetRequest.getNewPassword());
        return buildSuccessResponse(HttpStatus.OK.value(), "Password reset successfully", null);
    }

    /**
     * POST /api/auth/phone/send-otp
     * Send OTP to ANY phone number — does NOT require phone to be a registered
     * user.
     * Used for verifying customer phone numbers during customer onboarding.
     */
    @PostMapping("/phone/send-otp")
    public ResponseEntity<?> sendPhoneOtp(@Valid @RequestBody PhoneOtpRequest req) {
        String result = phoneVerificationService.sendOtp(req.getPhone());
        return buildSuccessResponse(HttpStatus.OK.value(), "OTP sent successfully", result);
    }

    /**
     * POST /api/auth/phone/verify-otp
     * Verify OTP for any phone number — does NOT create a session or return tokens.
     * Returns {verified: true} if OTP is valid.
     */
    @PostMapping("/phone/verify-otp")
    public ResponseEntity<?> verifyPhoneOtp(@Valid @RequestBody PhoneOtpRequest req) {
        phoneVerificationService.verifyOtp(req.getPhone(), req.getOtp());
        return buildSuccessResponse(HttpStatus.OK.value(), "Phone number verified",
                java.util.Map.of("verified", true));
    }

    private <T> ResponseEntity<?> buildSuccessResponse(int status, String message, T data) {
        ApiResponse<T> response = ApiResponse.success(status, message, data);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }

    private ResponseEntity<?> buildErrorResponse(int status, String message) {
        ApiResponse<?> response = ApiResponse.error(status, message);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }
}
