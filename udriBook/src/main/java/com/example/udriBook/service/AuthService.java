package com.example.udriBook.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.udriBook.dto.LoginRequest;
import com.example.udriBook.dto.RegisterRequest;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.entity.UserSession;
import com.example.udriBook.exception.CustomException;
import com.example.udriBook.repository.UserRepository;
import com.example.udriBook.repository.UserSessionRepository;
import com.example.udriBook.util.JwtUtil;
import com.example.udriBook.util.OtpUtil;
import com.example.udriBook.util.PasswordUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication Service — Handles all user auth flows:
 * - Registration
 * - Password login
 * - OTP login (phone via MSG91, email via local Redis OTP)
 * - Token refresh
 * - Logout (single device, specific device, all devices)
 * - Password reset
 * - Active session listing
 *
 * Security model:
 * - Access tokens: short-lived (15 min), stored in Redis for blacklisting
 * - Refresh tokens: long-lived (30 days), stored in Redis for revocation
 * - Sessions: also tracked in MySQL for audit/history
 * - Max 5 concurrent sessions per user (device limit)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository          userRepo;
    private final UserSessionRepository   userSessionRepo;
    private final PasswordUtil            passwordUtil;
    private final OtpUtil                 otpUtil;
    private final EmailService            emailService;
    private final JwtUtil                 jwtUtil;
    private final RedisSessionService     redisSessionService;
    private final Msg91Service            msg91Service;

    private static final int MAX_SESSIONS_PER_USER = 3;

    // ─────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────

    /**
     * Register a new user.
     * Validates uniqueness of phone and email before persisting.
     *
     * @throws CustomException if phone or email already registered
     */
    @Transactional
    public void register(RegisterRequest req) {
        if (userRepo.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new CustomException("Phone number already registered");
        }
        if (req.getEmailId() != null && !req.getEmailId().isBlank()
                && userRepo.existsByEmailId(req.getEmailId())) {
            throw new CustomException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setOwnerName(req.getOwnerName());
        user.setStoreName(req.getStoreName());
        user.setStoreType(req.getStoreType());
        user.setPhoneNumber(req.getPhoneNumber());
        user.setEmailId(req.getEmailId());
        user.setVillageName(req.getVillageName());
        user.setPincode(req.getPincode());
        user.setPasswordHash(passwordUtil.hashPassword(req.getPassword()));

        userRepo.save(user);
        log.info("New user registered: phone={}XX", req.getPhoneNumber().substring(0,
            Math.min(6, req.getPhoneNumber().length())));
    }

    // ─────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────

    /**
     * Authenticate user with password.
     *
     * @param req        Login request (phone/email + password)
     * @param deviceInfo Client device description (e.g. "Android 13, Samsung S22")
     * @param ipAddress  Client IP address
     * @return Login response map with tokens and user info
     */
    public Map<String, Object> loginWithPassword(LoginRequest req, String deviceInfo, String ipAddress) {
        UserEntity user = userRepo.findByPhoneOrEmail(req.getPhoneOrEmail())
            .orElseThrow(() -> new CustomException("Invalid Phone/Email"));

        if (!passwordUtil.matchPassword(req.getPassword(), user.getPasswordHash())) {
            throw new CustomException("Invalid password");
        }

        enforceSessionLimit(user.getId());
        return buildLoginResponse(user, deviceInfo, ipAddress);
    }

    // ─────────────────────────────────────────────────────────────────
    // OTP Send / Verify
    // ─────────────────────────────────────────────────────────────────

    /**
     * Send OTP to a registered phone or email.
     * - Phone: uses MSG91 in production, local Redis OTP in dev mode
     * - Email: always uses local Redis OTP sent via email
     *
     * IMPORTANT: We verify the user EXISTS before sending OTP.
     * This prevents OTP-based user enumeration only partially — consider
     * returning a fixed message regardless of existence for higher security.
     *
     * @param phoneOrEmail Registered phone number or email address
     * @return Fixed success message (production) or OTP itself (dev mode only)
     */
    public String sendOtp(String phoneOrEmail) {
        // Verify the user exists — throw a meaningful error to caller
        userRepo.findByPhoneOrEmail(phoneOrEmail).orElseThrow(() -> {
            if (isEmail(phoneOrEmail)) {
                return new CustomException("Email ID not registered");
            }
            return new CustomException("Phone number not registered");
        });

        if (isEmail(phoneOrEmail)) {
            // Email OTP — generate locally and send via email
            String otp = otpUtil.generateOtp(phoneOrEmail);
            emailService.sendOtpEmail(phoneOrEmail, otp);
            return "OTP sent to your email";
        }

        // Phone OTP
        if (msg91Service.isEnabled()) {
            boolean sent = msg91Service.sendOtp(phoneOrEmail);
            if (!sent) {
                throw new CustomException("Failed to send OTP. Please try again.");
            }
            return "OTP sent to your mobile number";
        } else {
            // Dev/test fallback — generate locally
            String otp = otpUtil.generateOtp(phoneOrEmail);
            log.info("DEV MODE — OTP for phone {}: {}", phoneOrEmail, otp);
            return otp;
        }
    }

    /**
     * Verify OTP and return tokens if valid.
     *
     * - Phone + MSG91 enabled: verifies via MSG91
     * - Email or dev mode: verifies from Redis
     *
     * @param phoneOrEmail Phone or email that received the OTP
     * @param otp          OTP entered by user
     * @param deviceInfo   Device description
     * @param ipAddress    Client IP
     * @return Login response map with tokens and user info
     */
    public Map<String, Object> verifyOtp(
            String phoneOrEmail, String otp,
            String deviceInfo, String ipAddress) {

        boolean otpValid;
        if (!isEmail(phoneOrEmail) && msg91Service.isEnabled()) {
            otpValid = msg91Service.verifyOtp(phoneOrEmail, otp);
        } else {
            otpValid = otpUtil.validateOtp(phoneOrEmail, otp);
        }

        if (!otpValid) {
            throw new CustomException("Invalid OTP");
        }

        UserEntity user = userRepo.findByPhoneOrEmail(phoneOrEmail)
            .orElseThrow(() -> new CustomException("User not found"));

        enforceSessionLimit(user.getId());
        return buildLoginResponse(user, deviceInfo, ipAddress);
    }

    // ─────────────────────────────────────────────────────────────────
    // Token Refresh
    // ─────────────────────────────────────────────────────────────────

    /**
     * Issue a new access token using a valid refresh token.
     * Validates both JWT signature and Redis presence (revocation check).
     *
     * @param refreshToken The refresh token from the client
     * @return Map with new accessToken, tokenType, and expiresIn
     */
    public Map<String, Object> refreshAccessToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new CustomException("Invalid or expired refresh token. Please login again.");
        }

        Map<Object, Object> tokenData = redisSessionService.getRefreshTokenData(refreshToken);
        if (tokenData == null || tokenData.isEmpty()) {
            throw new CustomException("Refresh token has been revoked. Please login again.");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        Long userId     = Long.parseLong((String) tokenData.get("userId"));
        String newAccessToken = jwtUtil.generateAccessToken(username, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("tokenType",   "Bearer");
        response.put("expiresIn",   jwtUtil.getAccessTokenExpiryMs() / 1000);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────

    /**
     * Logout current session — blacklist access token and revoke refresh token.
     *
     * @param accessToken  Bearer token from Authorization header
     * @param refreshToken Refresh token from request body
     */
    @Transactional
    public void logoutWithTokens(String accessToken, String refreshToken) {
        String cleanAccess = stripBearer(accessToken);

        // Blacklist the access token in Redis (auto-expires with token TTL)
        if (cleanAccess != null && !cleanAccess.isBlank()) {
            long remainingTtl = jwtUtil.getRemainingTtlSeconds(cleanAccess);
            redisSessionService.blacklistToken(cleanAccess, remainingTtl);

            // Mark session inactive in MySQL (for audit trail)
            userSessionRepo.findByToken(cleanAccess).ifPresent(session -> {
                session.setActive(false);
                userSessionRepo.save(session);
            });
        }

        // Revoke refresh token from Redis
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Long userId = jwtUtil.extractUserId(refreshToken);
                redisSessionService.revokeRefreshToken(refreshToken, userId);
            } catch (Exception e) {
                // Non-fatal — log and continue
                log.warn("Could not revoke refresh token during logout: {}", e.getMessage());
            }
        }
    }

    /**
     * Legacy single-token logout (access token only).
     * Prefer logoutWithTokens() when both tokens are available.
     *
     * @param token Bearer token from Authorization header
     */
    @Transactional
    public void logout(String token) {
        logoutWithTokens(token, null);
    }

    /**
     * Logout from all devices for the user who owns the given token.
     * Revokes all refresh tokens and blacklists all access tokens.
     *
     * @param token Bearer token of the requesting user
     */
    @Transactional
    public void logoutAll(String token) {
        String cleanToken = stripBearer(token);
        UserSession currentSession = userSessionRepo.findByToken(cleanToken)
            .orElseThrow(() -> new CustomException("Invalid session"));

        Long userId = currentSession.getUser().getId();

        // Revoke all refresh tokens in Redis
        redisSessionService.revokeAllUserTokens(userId);

        // Blacklist all active access tokens and mark sessions inactive
        List<UserSession> sessions = userSessionRepo.findByUserIdAndActiveTrue(userId);
        sessions.forEach(s -> {
            s.setActive(false);
            long ttl = jwtUtil.getRemainingTtlSeconds(s.getToken());
            if (ttl > 0) {
                redisSessionService.blacklistToken(s.getToken(), ttl);
            }
        });
        userSessionRepo.saveAll(sessions);
        log.info("Logged out all {} sessions for userId={}", sessions.size(), userId);
    }

    /**
     * Logout a specific device session (e.g. from "Manage Devices" screen).
     * Only the owner of the session can remove it.
     *
     * @param token     Bearer token of the requesting user
     * @param sessionId ID of the session to remove
     */
    @Transactional
    public void logoutDevice(String token, Long sessionId) {
        String cleanToken = stripBearer(token);
        UserSession currentSession = userSessionRepo.findByToken(cleanToken)
            .orElseThrow(() -> new CustomException("Invalid session"));

        UserSession targetSession = userSessionRepo.findById(sessionId)
            .orElseThrow(() -> new CustomException("Device session not found"));

        // Security: ensure the session belongs to the requesting user
        if (!targetSession.getUser().getId().equals(currentSession.getUser().getId())) {
            throw new CustomException("Unauthorized: Cannot remove another user's session");
        }

        targetSession.setActive(false);
        userSessionRepo.save(targetSession);

        long ttl = jwtUtil.getRemainingTtlSeconds(targetSession.getToken());
        if (ttl > 0) {
            redisSessionService.blacklistToken(targetSession.getToken(), ttl);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Session Management
    // ─────────────────────────────────────────────────────────────────

    /**
     * List all active sessions for the user who owns the given token.
     * Marks the current session with isCurrent=true so the UI can highlight it.
     *
     * @param token Bearer token from Authorization header
     * @return List of session maps (id, deviceName, location, loginTime, isCurrent)
     */
    public List<Map<String, Object>> getActiveSessions(String token) {
        String cleanToken = stripBearer(token);
        UserSession currentSession = userSessionRepo.findByToken(cleanToken)
            .orElseThrow(() -> new CustomException("Invalid session"));

        return userSessionRepo
            .findByUserIdAndActiveTrue(currentSession.getUser().getId())
            .stream()
            .map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id",         s.getId().toString());
                map.put("deviceName", s.getDeviceInfo() != null ? s.getDeviceInfo() : "Unknown Device");
                map.put("location",   s.getIpAddress()  != null ? s.getIpAddress()  : "Unknown Location");
                map.put("loginTime",  s.getLoginTime().toString());
                map.put("isCurrent",  s.getToken().equals(cleanToken));
                map.put("active",     s.isActive());
                return map;
            })
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // Password Reset
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verify a password-reset OTP.
     * Uses MSG91 for phone, Redis OTP for email.
     */
    public boolean verifyResetOtp(String phoneOrEmail, String otp) {
        if (!isEmail(phoneOrEmail) && msg91Service.isEnabled()) {
            return msg91Service.verifyOtp(phoneOrEmail, otp);
        }
        return otpUtil.validateOtp(phoneOrEmail, otp);
    }

    /**
     * Reset user password.
     * Terminates ALL active sessions after reset as a security measure.
     *
     * @param phoneOrEmail Registered phone or email
     * @param newPassword  New plain-text password (will be hashed)
     */
    @Transactional
    public void resetPassword(String phoneOrEmail, String newPassword) {
        UserEntity user = userRepo.findByPhoneOrEmail(phoneOrEmail)
            .orElseThrow(() -> new CustomException("User not found"));

        user.setPasswordHash(passwordUtil.hashPassword(newPassword));
        userRepo.save(user);

        // Security: invalidate all sessions after password change
        List<UserSession> activeSessions = userSessionRepo.findByUserIdAndActiveTrue(user.getId());
        activeSessions.forEach(s -> {
            s.setActive(false);
            long ttl = jwtUtil.getRemainingTtlSeconds(s.getToken());
            if (ttl > 0) {
                redisSessionService.blacklistToken(s.getToken(), ttl);
            }
        });
        userSessionRepo.saveAll(activeSessions);
        redisSessionService.revokeAllUserTokens(user.getId());

        log.info("Password reset complete for userId={}. {} sessions terminated.", user.getId(), activeSessions.size());
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Enforce per-user concurrent session limit.
     * Counts active Redis sessions — throws if at or over limit.
     */
    private void enforceSessionLimit(Long userId) {
        long count = redisSessionService.countUserSessions(userId);
        
        // Fallback: If Redis is unavailable or disabled, it returns 0.
        // We verify the actual active session count from the database instead.
        if (count == 0) {
            count = userSessionRepo.countByUserIdAndActiveTrue(userId);
        }

        if (count >= MAX_SESSIONS_PER_USER) {
            throw new CustomException(
                "SESSION_LIMIT_EXCEEDED: Maximum " + MAX_SESSIONS_PER_USER +
                " devices allowed. Please logout from another device first.");
        }
    }

    /**
     * Build the standard login response (tokens + user info).
     * Called by both password login and OTP login.
     */
    @Transactional
    private Map<String, Object> buildLoginResponse(UserEntity user, String deviceInfo, String ipAddress) {
        String accessToken  = jwtUtil.generateAccessToken(user.getPhoneNumber(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getPhoneNumber(), user.getId());

        // Store refresh token in Redis for revocation tracking
        long refreshTtlSeconds = jwtUtil.getRefreshTokenExpiryMs() / 1000;
        redisSessionService.storeRefreshToken(refreshToken, user.getId(),
            user.getPhoneNumber(), refreshTtlSeconds);

        // Persist session to MySQL for audit/session-listing
        UserSession session = new UserSession();
        session.setUser(user);
        session.setToken(accessToken);
        session.setLoginTime(LocalDateTime.now());
        session.setDeviceInfo(deviceInfo);
        session.setIpAddress(ipAddress);
        session.setActive(true);
        userSessionRepo.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("userId",      user.getId());
        response.put("username",    user.getOwnerName());
        response.put("shopName",    user.getStoreName());
        response.put("storeType",   user.getStoreType());
        response.put("email",       user.getEmailId());
        response.put("phone",       user.getPhoneNumber());
        response.put("imageUrl",    user.getImageUrl());
        response.put("accessToken", accessToken);
        response.put("refreshToken",refreshToken);
        response.put("tokenType",   "Bearer");
        response.put("expiresIn",   jwtUtil.getAccessTokenExpiryMs() / 1000);
        return response;
    }

    /** Returns true if the identifier is an email address */
    private boolean isEmail(String value) {
        return value != null && value.contains("@");
    }

    /** Strip "Bearer " prefix from Authorization header value */
    private String stripBearer(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}