package com.example.udriBook.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP Utility — Redis-backed OTP generation and validation.
 *
 * Why Redis (not in-memory Map):
 * - Survives server restarts
 * - Works across multiple app instances (horizontal scaling)
 * - Auto-expires via Redis TTL — no manual cleanup needed
 * - SecureRandom for cryptographically safe OTP generation
 *
 * Key format: "otp:<identifier>"  (e.g. "otp:9876543210" or "otp:user@email.com")
 * TTL: 5 minutes (configurable via OTP_EXPIRY_MINUTES)
 * One-time use: deleted from Redis immediately after successful validation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpUtil {

    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${redis.enabled:true}")
    private boolean enabled;

    // Local fallback cache for when Redis is disabled
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .expireAfterWrite(OTP_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private static final String      OTP_PREFIX          = "otp:";
    private static final int         OTP_EXPIRY_MINUTES  = 5;
    private static final int         OTP_DIGITS          = 4;

    // SecureRandom is thread-safe and cryptographically strong
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate a 4-digit OTP and store it in Redis with a 5-minute TTL.
     * If an OTP already exists for this key, it is overwritten (handles resend).
     *
     * @param key Phone number or email address
     * @return The generated OTP (plain digits, e.g. "0471")
     */
    public String generateOtp(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("OTP key must not be blank");
        }
        // Zero-padded: ensures "0471" not "471"
        String otp     = String.format("%0" + OTP_DIGITS + "d", SECURE_RANDOM.nextInt((int) Math.pow(10, OTP_DIGITS)));
        String redisKey = OTP_PREFIX + key.trim();

        if (enabled) {
            try {
                redisTemplate.opsForValue().set(redisKey, otp, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
                log.debug("OTP stored in Redis for key hash: {}", key.hashCode());
            } catch (Exception e) {
                log.error("Redis unreachable during OTP storage, falling back to local cache: {}", e.getMessage());
                localCache.put(key.trim(), otp);
            }
        } else {
            localCache.put(key.trim(), otp);
            log.debug("OTP stored in local cache (Redis disabled) for key hash: {}", key.hashCode());
        }
        return otp;
    }

    /**
     * Validate an OTP against the stored value in Redis.
     * Deletes the OTP from Redis on success (one-time use).
     * Does NOT delete on failure — caller should track failed attempts.
     *
     * @param key Phone number or email address
     * @param otp OTP entered by user
     * @return true if OTP matches and is not expired
     */
    public boolean validateOtp(String key, String otp) {
        if (key == null || otp == null || key.isBlank() || otp.isBlank()) {
            return false;
        }

        String redisKey  = OTP_PREFIX + key.trim();
        String storedOtp = null;

        if (enabled) {
            try {
                storedOtp = redisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {
                log.error("Redis unreachable during OTP validation, checking local cache: {}", e.getMessage());
            }
        }

        // Check local cache if not found in Redis (or Redis disabled/failed)
        if (storedOtp == null) {
            storedOtp = localCache.getIfPresent(key.trim());
        }

        if (storedOtp == null) {
            log.debug("OTP expired or not found for key hash: {}", key.hashCode());
            return false;
        }

        if (storedOtp.equals(otp.trim())) {
            if (enabled) {
                try {
                    redisTemplate.delete(redisKey);
                } catch (Exception e) {
                    log.warn("Failed to delete verified OTP from Redis: {}", e.getMessage());
                }
            }
            localCache.invalidate(key.trim()); // One-time use: delete immediately
            log.debug("OTP validated and consumed for key hash: {}", key.hashCode());
            return true;
        }

        log.debug("OTP mismatch for key hash: {}", key.hashCode());
        return false;
    }

    /**
     * Explicitly delete/invalidate an OTP (e.g. after password reset is complete).
     *
     * @param key Phone number or email address
     */
    public void invalidateOtp(String key) {
        if (key != null && !key.isBlank()) {
            String cleanKey = key.trim();
            if (enabled) {
                try {
                    redisTemplate.delete(OTP_PREFIX + cleanKey);
                } catch (Exception e) {
                    log.warn("Failed to invalidate OTP in Redis: {}", e.getMessage());
                }
            }
            localCache.invalidate(cleanKey);
        }
    }

    /**
     * Check if an OTP exists and is still valid (not expired) for the given key.
     * Does NOT consume the OTP.
     *
     * @param key Phone number or email address
     * @return true if a valid (non-expired) OTP exists
     */
    public boolean hasActiveOtp(String key) {
        if (key == null || key.isBlank()) return false;
        String cleanKey = key.trim();

        if (enabled) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(OTP_PREFIX + cleanKey))) {
                    return true;
                }
            } catch (Exception e) {
                log.error("Redis unreachable during hasKey check: {}", e.getMessage());
            }
        }
        return localCache.getIfPresent(cleanKey) != null;
    }
}