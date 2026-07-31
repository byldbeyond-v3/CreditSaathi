package com.example.udriBook.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OTP Rate Limiter — Protects OTP endpoints from two attack types:
 *
 * 1. SMS FLOODING (send rate limit):
 * - Max 3 OTP sends per phone number per 10 minutes
 * - Prevents draining MSG91 credits via automation
 * - Uses Bucket4j token-bucket algorithm
 * - Buckets stored in Caffeine cache with TTL — no memory leak
 *
 * 2. BRUTE FORCE (verify attempt limit):
 * - Max 5 wrong OTP attempts per phone number
 * - Locked for 15 minutes after 5 failures
 * - Auto-reset on successful verification
 * - Uses Caffeine cache with expireAfterWrite
 *
 * NOTE: This is in-memory. For multi-instance deployments (horizontal scaling),
 * replace Caffeine with Redis and Bucket4j with bucket4j-redis extension.
 * For a single-instance deployment (e.g., Railway, single EC2), this is fine.
 */
@Slf4j
@Service
public class OtpRateLimiterService {

    private static final int MAX_SEND_PER_WINDOW = 3;
    private static final int SEND_WINDOW_MINUTES = 10;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    /**
     * Bucket4j send-rate buckets stored in Caffeine cache.
     *
     * FIX: Previously used ConcurrentHashMap — grew unbounded (memory leak).
     * Now uses Caffeine with expireAfterAccess so infrequently-used phone
     * numbers are evicted automatically after 30 minutes of inactivity.
     */
    private final Cache<String, Bucket> sendBuckets = Caffeine.newBuilder()
            .expireAfterAccess(SEND_WINDOW_MINUTES * 3L, TimeUnit.MINUTES) // evict if idle
            .maximumSize(100_000) // hard cap: ~100K concurrent users in memory
            .build();

    // Track failed verify attempts per phone — auto-evicts after lockout window
    // expires
    private final Cache<String, AtomicInteger> failedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_MINUTES, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    // ─── Send Rate Limiting ───────────────────────────────────────────

    /**
     * Check if this phone is allowed to request a new OTP right now.
     * Allows MAX_SEND_PER_WINDOW (3) requests per SEND_WINDOW_MINUTES (10 min).
     *
     * @param mobileNumber Normalized 10-digit phone number
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allowSendOtp(String mobileNumber) {
        Bucket bucket = sendBuckets.get(mobileNumber, this::createSendBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Send OTP rate limit exceeded for: {}", maskNumber(mobileNumber));
        }
        return allowed;
    }

    // ─── Verify Brute-Force Protection ───────────────────────────────

    /**
     * Check if this phone is allowed to attempt OTP verification.
     * Blocked after MAX_VERIFY_ATTEMPTS (5) failures until TTL expires (15 min).
     *
     * @param mobileNumber Normalized 10-digit phone number
     * @return true if allowed, false if brute-force lockout is active
     */
    public boolean allowVerifyOtp(String mobileNumber) {
        AtomicInteger attempts = failedAttempts.getIfPresent(mobileNumber);
        if (attempts != null && attempts.get() >= MAX_VERIFY_ATTEMPTS) {
            log.warn("Brute-force lockout active for: {}", maskNumber(mobileNumber));
            return false;
        }
        return true;
    }

    /**
     * Record a failed OTP verification attempt.
     * Call this whenever OTP verification returns false/invalid.
     *
     * @param mobileNumber Normalized 10-digit phone number
     */
    public void recordFailedVerification(String mobileNumber) {
        AtomicInteger attempts = failedAttempts.get(mobileNumber, k -> new AtomicInteger(0));
        int count = attempts.incrementAndGet();
        if (count >= MAX_VERIFY_ATTEMPTS) {
            log.warn("Max OTP attempts ({}) reached for {} — locked for {} min",
                    MAX_VERIFY_ATTEMPTS, maskNumber(mobileNumber), LOCKOUT_MINUTES);
        } else {
            log.debug("Failed OTP attempt {}/{} for {}", count, MAX_VERIFY_ATTEMPTS, maskNumber(mobileNumber));
        }
    }

    /**
     * Clear failed attempt counter after successful OTP verification.
     * Must be called on success to unlock any active lockout.
     *
     * @param mobileNumber Normalized 10-digit phone number
     */
    public void clearFailedAttempts(String mobileNumber) {
        failedAttempts.invalidate(mobileNumber);
        log.debug("Cleared failed attempts for {}", maskNumber(mobileNumber));
    }

    /**
     * Get remaining verification attempts before lockout.
     * Use this to show "X attempts remaining" in the Flutter UI.
     *
     * @param mobileNumber Normalized 10-digit phone number
     * @return remaining attempts, 0 if locked out
     */
    public int getRemainingAttempts(String mobileNumber) {
        AtomicInteger attempts = failedAttempts.getIfPresent(mobileNumber);
        int used = (attempts != null) ? attempts.get() : 0;
        return Math.max(0, MAX_VERIFY_ATTEMPTS - used);
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private Bucket createSendBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(MAX_SEND_PER_WINDOW)
                        .refillIntervally(MAX_SEND_PER_WINDOW, Duration.ofMinutes(SEND_WINDOW_MINUTES))
                        .build())
                .build();
    }

    private String maskNumber(String number) {
        if (number == null || number.length() < 5)
            return "****";
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() < 5)
            return "****";
        return digits.substring(0, 2) + "XXXXX" + digits.substring(digits.length() - 3);
    }
}