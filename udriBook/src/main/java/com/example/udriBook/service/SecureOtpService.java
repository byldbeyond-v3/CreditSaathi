package com.example.udriBook.service;

import com.example.udriBook.exception.OtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * SecureOtpService — Production-ready OTP generation and verification.
 *
 * Design decisions:
 * ─────────────────
 * ✅ SecureRandom     → cryptographically strong 6-digit OTP (not Math.random)
 * ✅ BCrypt hash      → OTPs are never stored in plain text in Redis
 * ✅ Redis TTL        → OTP auto-expires after 5 minutes, no manual cleanup needed
 * ✅ Rate limiting    → max 3 generate requests per minute (via Redis counter)
 * ✅ Brute force      → max 5 verify attempts, OTP deleted on exhaustion
 * ✅ One-time use     → OTP deleted from Redis immediately on success
 * ✅ Masked logging   → phone/email never logged in full
 *
 * Redis Key Schema:
 * ─────────────────
 * otp:{identifier}              → BCrypt-hashed OTP      TTL: 5 min
 * otp_gen_rate:{identifier}     → generation counter     TTL: 1 min
 * otp_attempts:{identifier}     → verification counter   TTL: 5 min
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecureOtpService {

    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${redis.enabled:false}")
    private boolean redisEnabled;

    // In-memory fallbacks for local development without Redis
    private static final java.util.concurrent.ConcurrentHashMap<String, String> otpStore = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> otpExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> attemptsStore = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> rateStore = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> rateExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> verifiedStore = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * BCrypt PasswordEncoder — injected from SecurityConfig.
     * Used to hash the OTP before storing in Redis.
     */
    private final PasswordEncoder passwordEncoder;

    // ─── Config constants ─────────────────────────────────────────────
    private static final String OTP_PREFIX      = "otp:";
    private static final String GEN_RATE_PREFIX = "otp_gen_rate:";
    private static final String ATTEMPTS_PREFIX = "otp_attempts:";

    private static final int OTP_DIGITS               = 6;
    private static final int OTP_EXPIRY_MINUTES       = 5;
    private static final int MAX_GENERATE_PER_MINUTE  = 3;
    private static final int MAX_VERIFY_ATTEMPTS      = 5;

    /**
     * SecureRandom is thread-safe — one instance shared across all calls.
     * 'new SecureRandom()' seeds from OS entropy pool (e.g. /dev/urandom on Linux).
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ─── Public API ───────────────────────────────────────────────────

    /**
     * @param identifier Phone number or email address
     * @return Raw 6-digit OTP (to be sent via SMS/Email — never log this)
     * @throws OtpException if identifier is invalid or rate limit exceeded
     */
    public String generateOtp(String identifier) {
        // 1. Validate
        String cleanId = validateAndNormalize(identifier);

        // 2. Rate limit: max MAX_GENERATE_PER_MINUTE requests per minute
        enforceGenerateRateLimit(cleanId);

        // 3. Generate secure 6-digit OTP (zero-padded: "042795" not "42795")
        int max = (int) Math.pow(10, OTP_DIGITS);
        String otp = String.format("%0" + OTP_DIGITS + "d", SECURE_RANDOM.nextInt(max));

        // 4. Hash OTP with BCrypt BEFORE storing — raw OTP is never persisted
        String hashedOtp = passwordEncoder.encode(otp);
        String otpKey = OTP_PREFIX + cleanId;
        
        boolean storedInRedis = false;
        if (redisEnabled) {
            try {
                redisTemplate.opsForValue().set(otpKey, hashedOtp, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
                redisTemplate.delete(ATTEMPTS_PREFIX + cleanId);
                storedInRedis = true;
            } catch (Exception e) {
                log.error("Redis error during OTP storage: {}. Falling back to in-memory.", e.getMessage());
            }
        }

        if (!storedInRedis) {
            // Fallback to in-memory
            otpStore.put(otpKey, hashedOtp);
            otpExpiry.put(otpKey, System.currentTimeMillis() + (OTP_EXPIRY_MINUTES * 60 * 1000L));
            attemptsStore.remove(ATTEMPTS_PREFIX + cleanId);
            log.info("OTP stored in-memory for [{}]", mask(cleanId));
        }

        log.info("OTP generated for identifier [{}]", mask(cleanId));

        // 6. Return plain OTP — caller is responsible for sending it via SMS/Email
        return otp;
    }

    /**
     
     * @param identifier Phone number or email address
     * @param otpEntered OTP entered by the user
     * @return true if OTP is valid
     * @throws OtpException on invalid OTP, exhausted attempts, or expired OTP
     */
    public boolean verifyOtp(String identifier, String otpEntered) {
        // 1. Validate
        String cleanId = validateAndNormalize(identifier);
        if (otpEntered == null || otpEntered.isBlank()) {
            throw new OtpException("OTP must not be empty");
        }

        // 2. Check attempt count
        String attemptsKey = ATTEMPTS_PREFIX + cleanId;
        String otpKey      = OTP_PREFIX + cleanId;
        int currentAttempts = 0;

        if (redisEnabled) {
            try {
                String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
                currentAttempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
            } catch (Exception e) {
                log.error("Redis error fetching attempts: {}. Checking memory.", e.getMessage());
                currentAttempts = attemptsStore.getOrDefault(attemptsKey, 0);
            }
        } else {
            currentAttempts = attemptsStore.getOrDefault(attemptsKey, 0);
        }

        if (currentAttempts >= MAX_VERIFY_ATTEMPTS) {
            deleteOtpData(otpKey, attemptsKey);
            throw new OtpException(
                "Maximum verification attempts exceeded. Please generate a new OTP.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // 3. Retrieve hashed OTP
        String storedHash = null;
        if (redisEnabled) {
            try {
                storedHash = redisTemplate.opsForValue().get(otpKey);
            } catch (Exception e) {
                log.error("Redis error fetching OTP: {}", e.getMessage());
            }
        }
        
        // Fallback to memory if Redis failed or returned nothing
        if (storedHash == null) {
            Long expiry = otpExpiry.get(otpKey);
            if (expiry != null && System.currentTimeMillis() < expiry) {
                storedHash = otpStore.get(otpKey);
            }
        }

        if (storedHash == null) {
            throw new OtpException("OTP has expired or does not exist. Please generate a new OTP.");
        }

        // 4. BCrypt comparison
        boolean matches = passwordEncoder.matches(otpEntered.trim(), storedHash);

        if (!matches) {
            int newCount = currentAttempts + 1;
            if (redisEnabled) {
                try {
                    Long count = redisTemplate.opsForValue().increment(attemptsKey);
                    if (count != null && count == 1) {
                        redisTemplate.expire(attemptsKey, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
                    }
                    newCount = count != null ? count.intValue() : newCount;
                } catch (Exception e) {
                    log.error("Redis error incrementing attempts: {}", e.getMessage());
                }
            }
            attemptsStore.put(attemptsKey, newCount);

            int remaining = MAX_VERIFY_ATTEMPTS - newCount;
            String suffix = remaining > 0
                ? " " + remaining + " attempt(s) remaining."
                : " No attempts remaining. Please generate a new OTP.";
            log.warn("OTP mismatch for [{}]. Attempts: {}/{}", mask(cleanId), newCount, MAX_VERIFY_ATTEMPTS);
            throw new OtpException("Invalid OTP." + suffix);
        }

        // 5. Success
        deleteOtpData(otpKey, attemptsKey);
        log.info("OTP verified successfully for [{}]", mask(cleanId));

        return true;
    }

    /**
     * Check if a valid (non-expired) OTP already exists for this identifier.
     * Useful before sending a new OTP to show "OTP already sent, check your phone".
     *
     * @param identifier Phone number or email
     * @return true if an active OTP exists in Redis
     */
    public boolean hasActiveOtp(String identifier) {
        String cleanId = validateAndNormalize(identifier);
        String key = OTP_PREFIX + cleanId;
        
        if (redisEnabled) {
            try {
                Boolean hasKey = redisTemplate.hasKey(key);
                if (Boolean.TRUE.equals(hasKey)) return true;
            } catch (Exception e) {
                log.error("Redis error in hasActiveOtp: {}", e.getMessage());
            }
        }
        
        Long expiry = otpExpiry.get(key);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    // ─── Phone Pre-Verification (before customer creation) ───────────
    //
    // When mobile is verified BEFORE the customer is saved (no customerId yet),
    // we store a short-lived "phone_verified:{mobile}" flag in Redis.
    // addCustomer() checks this flag and sets mobileVerified=true, then deletes it.
    //
    // Redis key: phone_verified:{mobile}  →  "1"   TTL: 15 minutes

    private static final String PHONE_VERIFIED_PREFIX = "phone_verified:";
    private static final int    PHONE_VERIFIED_TTL_MINUTES = 15;

    /**
     * Store a short-lived "verified" marker in Redis for the given mobile number.
     * Call this immediately after a successful OTP verification.
     * TTL is 15 minutes — the shopkeeper should complete the customer-add form within that window.
     *
     * @param mobile Normalized mobile number
     */
    public void markPhoneVerified(String mobile) {
        String cleanMobile = validateAndNormalize(mobile);
        String key = PHONE_VERIFIED_PREFIX + cleanMobile;
        
        boolean storedInRedis = false;
        if (redisEnabled) {
            try {
                redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(PHONE_VERIFIED_TTL_MINUTES));
                storedInRedis = true;
            } catch (Exception e) {
                log.error("Redis error marking phone verified: {}", e.getMessage());
            }
        }
        
        if (!storedInRedis) {
            verifiedStore.put(key, System.currentTimeMillis() + (PHONE_VERIFIED_TTL_MINUTES * 60 * 1000L));
        }
        
        log.info("Phone [{}] marked as pre-verified (valid for {} min)", mask(cleanMobile), PHONE_VERIFIED_TTL_MINUTES);
    }

    /**
     * Check whether a mobile number was pre-verified by OTP, then consume the flag.
     * This is called inside addCustomer() to decide whether to set mobileVerified=true.
     *
     * One-time: flag is deleted after reading so it cannot be reused.
     *
     * @param mobile Mobile number to check
     * @return true if the phone was OTP-verified within the last 15 minutes
     */
    public boolean consumePhoneVerified(String mobile) {
        if (mobile == null || mobile.isBlank()) return false;
        String cleanMobile = mobile.trim().toLowerCase();
        String key = PHONE_VERIFIED_PREFIX + cleanMobile;
        
        boolean verified = false;
        if (redisEnabled) {
            try {
                Boolean exists = redisTemplate.hasKey(key);
                if (Boolean.TRUE.equals(exists)) {
                    redisTemplate.delete(key);
                    verified = true;
                }
            } catch (Exception e) {
                log.error("Redis error consuming verification: {}", e.getMessage());
            }
        }
        
        if (!verified) {
            Long expiry = verifiedStore.remove(key);
            verified = expiry != null && System.currentTimeMillis() < expiry;
        }

        if (verified) {
            log.info("Phone [{}] pre-verification consumed during customer creation", mask(cleanMobile));
            return true;
        }
        return false;
    }


    // ─── Private helpers ──────────────────────────────────────────────

   
    private void enforceGenerateRateLimit(String cleanId) {
        String rateLimitKey = GEN_RATE_PREFIX + cleanId;
        int count = 0;

        if (redisEnabled) {
            try {
                Long redisCount = redisTemplate.opsForValue().increment(rateLimitKey);
                if (redisCount != null && redisCount == 1) {
                    redisTemplate.expire(rateLimitKey, Duration.ofMinutes(1));
                }
                count = redisCount != null ? redisCount.intValue() : 1;
            } catch (Exception e) {
                log.error("Redis error during rate limit check: {}", e.getMessage());
            }
        }

        // Track in memory as well (or as fallback)
        Long expiry = rateExpiry.get(rateLimitKey);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            rateStore.put(rateLimitKey, new java.util.concurrent.atomic.AtomicInteger(1));
            rateExpiry.put(rateLimitKey, System.currentTimeMillis() + 60000);
            if (count == 0) count = 1;
        } else {
            int memCount = rateStore.get(rateLimitKey).incrementAndGet();
            count = Math.max(count, memCount);
        }

        if (count > MAX_GENERATE_PER_MINUTE) {
            log.warn("OTP generate rate limit exceeded for [{}]: {} requests/min", mask(cleanId), count);
            throw new OtpException(
                "Too many OTP requests. Please wait 1 minute before trying again.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    private void deleteOtpData(String otpKey, String attemptsKey) {
        if (redisEnabled) {
            try {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptsKey);
            } catch (Exception e) {
                log.error("Redis error deleting OTP data: {}", e.getMessage());
            }
        }
        otpStore.remove(otpKey);
        otpExpiry.remove(otpKey);
        attemptsStore.remove(attemptsKey);
    }

    /**
     * Validate and trim the identifier.
     * Accepts phone numbers (digits, spaces, dashes, plus) or email addresses.
     *
     * @param identifier Raw input from client
     * @return Trimmed identifier
     * @throws OtpException if null or blank
     */
    private String validateAndNormalize(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new OtpException("Phone number or email must not be empty");
        }
        return identifier.trim().toLowerCase();
    }

    private String mask(String identifier) {
        if (identifier == null || identifier.length() < 4) return "****";
        if (identifier.contains("@")) {
            // Email masking
            int at = identifier.indexOf('@');
            String local = identifier.substring(0, at);
            String domain = identifier.substring(at);
            if (local.length() <= 2) return "***" + domain;
            return local.substring(0, 2) + "***" + domain;
        }
        // Phone masking
        return identifier.substring(0, 2) + "*****" + identifier.substring(identifier.length() - 3);
    }
}
