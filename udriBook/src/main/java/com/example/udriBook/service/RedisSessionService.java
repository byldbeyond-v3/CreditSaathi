package com.example.udriBook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * Redis-based session and token management.
 *
 * Replaces MySQL session lookup (5–20ms) with Redis (0.5ms) on every request.
 *
 * Key design decisions:
 * - Tokens are SHA-256 hashed before storing — raw JWTs never touch Redis
 * - Blacklist uses token's remaining TTL — Redis auto-cleans, no cron job needed
 * - Pipeline used for bulk deletes — N sessions = 1 Redis round trip, not N
 * - All Redis calls wrapped in try/catch — Redis failure degrades gracefully
 *
 * Redis key structure:
 *   blacklist:<tokenHash>        → "revoked"          (TTL = remaining token life)
 *   refresh:<tokenHash>          → {userId, username} (TTL = refresh token expiry)
 *   user:sessions:<userId>       → Set<tokenHash>     (TTL = refresh token expiry)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSessionService {

    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${redis.enabled:false}")
    private boolean enabled;

    private static final String BLACKLIST_PREFIX      = "blacklist:";
    private static final String REFRESH_PREFIX        = "refresh:";
    private static final String USER_SESSIONS_PREFIX  = "user:sessions:";

    // ─── Token Blacklist ──────────────────────────────────────────────

    /**
     * Blacklist an access token on logout.
     * TTL = remaining life of the JWT so Redis auto-expires it — no cleanup needed.
     *
     * @param token             raw JWT access token
     * @param remainingTtlSeconds seconds until the token naturally expires
     */
    public void blacklistToken(String token, long remainingTtlSeconds) {
        if (!enabled) return;
        if (remainingTtlSeconds <= 0) {
            log.debug("Token already expired, skipping blacklist");
            return;
        }
        try {
            String key = BLACKLIST_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(remainingTtlSeconds));
            log.debug("Token blacklisted with TTL: {}s", remainingTtlSeconds);
        } catch (Exception e) {
            // Non-fatal: log and continue — token will expire naturally via JWT TTL
            log.error("Failed to blacklist token in Redis: {}", e.getMessage());
        }
    }

    /**
     * Check if a token has been revoked.
     * Called on EVERY authenticated request — must be fast (~0.5ms).
     *
     * @param token raw JWT access token
     * @return true if blacklisted (revoked), false if valid or Redis is down
     */
    public boolean isTokenBlacklisted(String token) {
        if (!enabled) return false;
        try {
            String key = BLACKLIST_PREFIX + hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // ✅ FIX: Redis down → fail open (allow request) rather than crashing
            // Tradeoff: a logged-out token might work briefly if Redis is down.
            // Alternative (fail closed): return true here — but that locks out ALL users.
            log.error("Redis unavailable during blacklist check — failing open: {}", e.getMessage());
            return false;
        }
    }

    // ─── Refresh Token Management ─────────────────────────────────────

    /**
     * Store a refresh token in Redis with user details for revocation tracking.
     *
     * @param refreshToken  raw refresh JWT
     * @param userId        user's database ID
     * @param username      user's phone/email (for token refresh response)
     * @param ttlSeconds    refresh token validity in seconds
     */
    public void storeRefreshToken(String refreshToken, Long userId, String username, long ttlSeconds) {
        if (!enabled) return;
        try {
            String hash      = hashToken(refreshToken);
            String tokenKey  = REFRESH_PREFIX + hash;
            String userKey   = USER_SESSIONS_PREFIX + userId;

            Map<String, String> data = new HashMap<>();
            data.put("userId",   String.valueOf(userId));
            data.put("username", username);

            // ✅ Use pipeline — 2 writes + 2 expires = 1 Redis round trip
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForHash().putAll(tokenKey, data);
                    operations.expire(tokenKey, Duration.ofSeconds(ttlSeconds));
                    operations.opsForSet().add(userKey, hash);
                    // User sessions key TTL slightly longer than token TTL
                    operations.expire(userKey, Duration.ofSeconds(ttlSeconds + 60));
                    return null;
                }
            });

            log.debug("Refresh token stored for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to store refresh token in Redis for userId {}: {}", userId, e.getMessage());
            // Fail open: log error but do not throw exception, allowing login to proceed with MySQL session
        }
    }

    /**
     * Get refresh token data. Returns null if token is invalid or expired.
     *
     * @param refreshToken raw refresh JWT
     * @return map with userId and username, or null if not found
     */
    public Map<Object, Object> getRefreshTokenData(String refreshToken) {
        if (!enabled) return null;
        try {
            String key  = REFRESH_PREFIX + hashToken(refreshToken);
            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
            // Return null instead of empty map so callers can use simple null check
            return data.isEmpty() ? null : data;
        } catch (Exception e) {
            log.error("Failed to get refresh token data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Revoke a single refresh token (single device logout).
     *
     * @param refreshToken raw refresh JWT
     * @param userId       user's database ID (to remove from session set)
     */
    public void revokeRefreshToken(String refreshToken, Long userId) {
        if (!enabled) return;
        try {
            String hash    = hashToken(refreshToken);
            String tokenKey = REFRESH_PREFIX + hash;
            String userKey  = USER_SESSIONS_PREFIX + userId;

            // ✅ Pipeline: delete token + remove from user's session set in 1 round trip
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.delete(tokenKey);
                    if (userId != null) {
                        operations.opsForSet().remove(userKey, hash);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Failed to revoke refresh token for userId {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Revoke ALL refresh tokens for a user (logout from all devices).
     * Uses pipeline — regardless of how many sessions, this is 1 Redis round trip
     * for fetching hashes + 1 pipeline for all deletes.
     *
     * @param userId user's database ID
     */
    public void revokeAllUserTokens(Long userId) {
        if (!enabled) return;
        try {
            String userKey = USER_SESSIONS_PREFIX + userId;

            // Step 1: Get all token hashes for this user
            Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
            if (tokenHashes == null || tokenHashes.isEmpty()) {
                redisTemplate.delete(userKey);
                return;
            }

            // ✅ FIX: Step 2: Delete all tokens in a single pipeline (1 round trip)
            // Previous code did N individual deletes — N round trips
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (String hash : tokenHashes) {
                        operations.delete(REFRESH_PREFIX + hash);
                    }
                    operations.delete(userKey);
                    return null;
                }
            });

            log.debug("Revoked {} refresh tokens for userId: {}", tokenHashes.size(), userId);
        } catch (Exception e) {
            log.error("Failed to revoke all tokens for userId {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Count active sessions (refresh tokens) for a user.
     * Used to enforce per-user device limit (max 5 devices).
     *
     * @param userId user's database ID
     * @return number of active sessions, 0 if none or Redis is down
     */
    public long countUserSessions(Long userId) {
        if (!enabled) return 0L;
        try {
            String userKey = USER_SESSIONS_PREFIX + userId;
            Long count = redisTemplate.opsForSet().size(userKey);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to count sessions for userId {}: {}", userId, e.getMessage());
            return 0L; // Fail open — don't block login if Redis is temporarily down
        }
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    /**
     * SHA-256 hash a token for safe Redis storage.
     * Raw JWTs are never stored in Redis keys or values.
     *
     * Uses ThreadLocal to avoid creating a new MessageDigest instance per call.
     * MessageDigest.getInstance() does a provider registry lookup — expensive
     * when called thousands of times per second on every authenticated request.
     */
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    });

    public static String hashToken(String token) {
        try {
            MessageDigest digest = SHA256_DIGEST.get();
            digest.reset(); // reset before reuse — ThreadLocal instances are reused
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hex string
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}