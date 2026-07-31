package com.example.udriBook.service;

import com.example.udriBook.exception.CustomException;
import com.example.udriBook.util.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Phone Verification Service — Verifies any mobile number via OTP.
 *
 * IMPORTANT: This service is intentionally independent of user accounts.
 * It is used for verifying customer phone numbers during onboarding,
 * NOT for user authentication (that is handled by AuthService).
 *
 * Flow:
 * 1. sendOtp(phone) → rate-check → sends OTP via MSG91 or dev-mode fallback
 * 2. verifyOtp(phone, otp) → brute-force-check → validates OTP → clears attempt
 * counter
 *
 * Rate limiting:
 * - Max 3 OTP sends per phone per 10 minutes (prevents SMS credit drain)
 * - Max 5 failed verify attempts before 15-minute lockout (prevents brute
 * force)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final OtpUtil otpUtil;
    private final Msg91Service msg91Service;
    private final OtpRateLimiterService rateLimiter; // ← wired in (was previously dead code)

    /**
     * Send OTP to any mobile number (no user registration check).
     *
     * In production (MSG91 enabled): delegates to MSG91, returns fixed message.
     * In dev mode (MSG91 disabled): generates OTP in Redis, returns OTP in response
     * so dev/QA can verify without a real SIM.
     *
     * @param phone Indian mobile number (any format)
     * @return Fixed message in production; actual OTP in dev mode only
     * @throws CustomException if phone is invalid, rate limited, or MSG91 fails
     */
    public String sendOtp(String phone) {
        String normalized = normalizeAndValidate(phone);

        // ── Rate limit check: max 3 sends per 10 min per phone ──────────
        if (!rateLimiter.allowSendOtp(normalized)) {
            throw new CustomException(
                    "Too many OTP requests. Please wait 10 minutes before requesting again.");
        }

        if (msg91Service.isEnabled()) {
            boolean sent = msg91Service.sendOtp(normalized);
            if (!sent) {
                throw new CustomException("Failed to send OTP. Please try again.");
            }
            log.info("Phone OTP sent via MSG91 to: {}XX", normalized.substring(0, normalized.length() - 2));
            // ✅ Production: NEVER expose the OTP in the API response
            return "OTP sent to your mobile number";
        } else {
            // ✅ Dev/test mode — generate locally, return OTP for testing without a real SIM
            String otp = otpUtil.generateOtp(normalized);
            log.warn("DEV MODE — OTP generated for {}: {} (disable MSG91=false in production)",
                    maskPhone(normalized), otp);
            return otp;
        }
    }

    /**
     * Verify OTP for a phone number.
     * Enforces brute-force lockout (max 5 wrong attempts → 15-min lock).
     *
     * @param phone Phone number (any format — will be normalized)
     * @param otp   OTP entered by user
     * @return true if OTP is valid
     * @throws CustomException if phone/OTP missing, locked out, or OTP
     *                         invalid/expired
     */
    public boolean verifyOtp(String phone, String otp) {
        if (otp == null || otp.isBlank()) {
            throw new CustomException("OTP is required");
        }

        String normalized = normalizeAndValidate(phone);

        // ── Brute-force lockout check ────────────────────────────────────
        if (!rateLimiter.allowVerifyOtp(normalized)) {
            int remaining = rateLimiter.getRemainingAttempts(normalized);
            throw new CustomException(
                    "Too many failed attempts. Try again in 15 minutes. Remaining attempts: " + remaining);
        }

        boolean valid;
        if (msg91Service.isEnabled()) {
            valid = msg91Service.verifyOtp(normalized, otp);
        } else {
            valid = otpUtil.validateOtp(normalized, otp);
        }

        if (!valid) {
            // Record failure BEFORE throwing so the counter updates atomically
            rateLimiter.recordFailedVerification(normalized);
            int remaining = rateLimiter.getRemainingAttempts(normalized);
            String suffix = remaining > 0
                    ? " " + remaining + " attempt(s) remaining before lockout."
                    : " Account locked for 15 minutes.";
            throw new CustomException("Invalid or expired OTP." + suffix);
        }

        // ── Success: clear lockout counter ───────────────────────────────
        rateLimiter.clearFailedAttempts(normalized);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Strip non-digits, take last 10 digits (handles country code prefix),
     * and validate the result is exactly 10 digits.
     *
     * @throws CustomException if phone is null, blank, or not 10 digits after
     *                         stripping
     */
    private String normalizeAndValidate(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new CustomException("Phone number is required");
        }
        String digits = phone.replaceAll("[^0-9]", "");
        // Accept "919876543210" (12 digits with country code) or "9876543210" (10
        // digits)
        String normalized = digits.length() > 10
                ? digits.substring(digits.length() - 10)
                : digits;
        if (normalized.length() != 10) {
            throw new CustomException("Enter a valid 10-digit mobile number");
        }
        return normalized;
    }

    /** Mask phone for safe logging: "9876543210" → "98XXXXX210" */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 5)
            return "****";
        return phone.substring(0, 2) + "XXXXX" + phone.substring(phone.length() - 3);
    }
}