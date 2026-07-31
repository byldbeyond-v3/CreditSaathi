package com.example.udriBook.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MSG91 SMS OTP Service — Production-ready implementation.
 *
 * Features:
 * - Startup validation (fails fast if config is missing)
 * - Input validation on all public methods
 * - Phone number masking in all logs (privacy)
 * - Automatic retry on transient network failures (not on 4xx)
 * - Micrometer metrics for monitoring (send/verify success & failure counts)
 * - Clean separation of HTTP logic
 *
 * Setup:
 * 1. Sign up at https://msg91.com → get Auth Key
 * 2. Create OTP Template → get Template ID
 * 3. Set environment variables: MSG91_AUTH_KEY, MSG91_TEMPLATE_ID
 * 4. Set msg91.enabled=true in application.properties
 */
@Slf4j
@Service
public class Msg91Service {

    @Value("${msg91.auth-key:}")
    private String authKey;

    @Value("${msg91.template-id:}")
    private String templateId;

    @Value("${msg91.sender-id:NMKHTA}")
    private String senderId;

    @Value("${msg91.otp-length:4}")
    private int otpLength;

    @Value("${msg91.otp-expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${msg91.enabled:false}")
    private boolean enabled;

    // Constructor-injected dependencies
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    // Metrics — registered in @PostConstruct after meterRegistry is available
    private Counter otpSentSuccess;
    private Counter otpSentFailure;
    private Counter otpVerifiedSuccess;
    private Counter otpVerifiedFailure;

    // Explicit constructor — required because @Value fields prevent
    // @RequiredArgsConstructor
    @org.springframework.beans.factory.annotation.Autowired
    public Msg91Service(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    private static final String SEND_OTP_URL = "https://control.msg91.com/api/v5/otp";
    private static final String VERIFY_OTP_URL = "https://control.msg91.com/api/v5/otp/verify";
    private static final String RESEND_OTP_URL = "https://control.msg91.com/api/v5/otp/retry";

    /**
     * Validates required config at startup.
     * Registers Micrometer counters for Grafana/Prometheus monitoring.
     * Throws IllegalStateException if MSG91 is enabled but keys are missing —
     * this is intentional: fail fast rather than silently at runtime.
     */
    @PostConstruct
    public void init() {
        if (enabled) {
            if (authKey == null || authKey.isBlank()) {
                throw new IllegalStateException(
                        "MSG91 is enabled but 'msg91.auth-key' is not configured. " +
                                "Set the MSG91_AUTH_KEY environment variable.");
            }
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalStateException(
                        "MSG91 is enabled but 'msg91.template-id' is not configured. " +
                                "Set the MSG91_TEMPLATE_ID environment variable.");
            }
            log.info("MSG91 ready | sender={} | otpLength={} | expiryMinutes={}",
                    senderId, otpLength, otpExpiryMinutes);
        } else {
            log.warn("MSG91 DISABLED — running in dev mode. Set msg91.enabled=true for production.");
        }

        // Register metrics regardless of enabled state so dashboards don't break
        otpSentSuccess = Counter.builder("msg91.otp.send").tag("result", "success").register(meterRegistry);
        otpSentFailure = Counter.builder("msg91.otp.send").tag("result", "failure").register(meterRegistry);
        otpVerifiedSuccess = Counter.builder("msg91.otp.verify").tag("result", "success").register(meterRegistry);
        otpVerifiedFailure = Counter.builder("msg91.otp.verify").tag("result", "failure").register(meterRegistry);
    }

    /**
     * Send OTP to an Indian mobile number via MSG91.
     * Retries up to 3 times on server/network errors. Does NOT retry on 4xx (bad
     * request).
     *
     * @param mobileNumber 10-digit Indian number (any format — with/without country
     *                     code)
     * @return true if MSG91 accepted the request
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = {
            HttpClientErrorException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2) // 1s,
                                                                                                                // then
                                                                                                                // 2s
    )
    public boolean sendOtp(String mobileNumber) {
        if (!enabled) {
            log.warn("MSG91 disabled. OTP not sent to {}", maskNumber(mobileNumber));
            return false;
        }
        if (!isValidMobile(mobileNumber)) {
            log.error("sendOtp — invalid mobile number: {}", maskNumber(mobileNumber));
            return false;
        }

        try {
            String formatted = formatIndianNumber(mobileNumber);

            Map<String, Object> body = new HashMap<>();
            body.put("template_id", templateId);
            body.put("mobile", formatted);
            body.put("sender", senderId);
            body.put("otp_length", otpLength);
            body.put("otp_expiry", otpExpiryMinutes);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, buildHeaders());

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    SEND_OTP_URL, HttpMethod.POST, request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            boolean success = isSuccess(response);
            if (success) {
                otpSentSuccess.increment();
                log.info("OTP sent successfully to {}", maskNumber(mobileNumber));
            } else {
                otpSentFailure.increment();
                log.warn("OTP send failed for {} — MSG91 reason: {}",
                        maskNumber(mobileNumber), extractMessage(response));
            }
            return success;

        } catch (HttpClientErrorException e) {
            // 4xx = bad request (e.g. wrong template, invalid number format) — don't retry
            otpSentFailure.increment();
            log.error("MSG91 rejected OTP send for {} — HTTP {}: {}",
                    maskNumber(mobileNumber), e.getStatusCode(), e.getResponseBodyAsString());
            return false;

        } catch (Exception e) {
            otpSentFailure.increment();
            // Log only the exception class — not the message (may contain auth key in
            // stack)
            log.error("OTP send error for {} — {}", maskNumber(mobileNumber), e.getClass().getSimpleName());
            throw e; // Rethrow so @Retryable can retry
        }
    }

    /**
     * Verify OTP entered by user against MSG91.
     *
     * @param mobileNumber Mobile number the OTP was sent to
     * @param otp          OTP entered by user
     * @return true if OTP is correct and not expired
     */
    public boolean verifyOtp(String mobileNumber, String otp) {
        if (!enabled) {
            log.warn("MSG91 disabled. Skipping OTP verification for {}", maskNumber(mobileNumber));
            return false;
        }
        if (!isValidMobile(mobileNumber)) {
            log.error("verifyOtp — invalid mobile number: {}", maskNumber(mobileNumber));
            return false;
        }
        // Validate OTP matches expected digit-only format
        if (otp == null || !otp.matches("\\d{" + otpLength + "}")) {
            log.warn("verifyOtp — invalid OTP format for {}", maskNumber(mobileNumber));
            return false;
        }

        try {
            String formatted = formatIndianNumber(mobileNumber);
            String url = VERIFY_OTP_URL + "?mobile=" + formatted + "&otp=" + otp;

            HttpEntity<String> request = new HttpEntity<>(buildHeaders());

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            boolean success = isSuccess(response);
            if (success) {
                otpVerifiedSuccess.increment();
                log.info("OTP verified for {}", maskNumber(mobileNumber));
            } else {
                otpVerifiedFailure.increment();
                log.warn("OTP verification failed for {} — reason: {}",
                        maskNumber(mobileNumber), extractMessage(response));
            }
            return success;

        } catch (Exception e) {
            otpVerifiedFailure.increment();
            log.error("OTP verify error for {} — {}", maskNumber(mobileNumber), e.getClass().getSimpleName());
            return false; // Don't retry verify — wrong OTP should not be retried
        }
    }

    /**
     * Resend (retry) OTP to a mobile number.
     * Retries on network errors, not on 4xx.
     *
     * @param mobileNumber Mobile number
     * @param retryType    "text" for SMS resend, "voice" for voice call
     * @return true if resend accepted by MSG91
     */
    @Retryable(retryFor = { Exception.class }, noRetryFor = {
            HttpClientErrorException.class }, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public boolean resendOtp(String mobileNumber, String retryType) {
        if (!enabled) {
            log.warn("MSG91 disabled. OTP resend skipped for {}", maskNumber(mobileNumber));
            return false;
        }
        if (!isValidMobile(mobileNumber)) {
            log.error("resendOtp — invalid mobile: {}", maskNumber(mobileNumber));
            return false;
        }
        // Default to "text" if retryType is null or unrecognized
        String safeRetryType = ("voice".equalsIgnoreCase(retryType)) ? "voice" : "text";

        try {
            String formatted = formatIndianNumber(mobileNumber);
            // MSG91 retry endpoint uses GET — not POST
            String url = RESEND_OTP_URL + "?mobile=" + formatted + "&retrytype=" + safeRetryType;

            HttpEntity<String> request = new HttpEntity<>(buildHeaders());

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            boolean success = isSuccess(response);
            log.info("OTP resend ({}) for {} — {}",
                    safeRetryType, maskNumber(mobileNumber), success ? "success" : "failed");
            return success;

        } catch (HttpClientErrorException e) {
            log.error("MSG91 rejected resend for {} — HTTP {}: {}",
                    maskNumber(mobileNumber), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("OTP resend error for {} — {}", maskNumber(mobileNumber), e.getClass().getSimpleName());
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /** Build standard MSG91 request headers */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", authKey);
        return headers;
    }

    /** Check MSG91 response for success */
    private boolean isSuccess(ResponseEntity<Map<String, Object>> response) {
        return response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null
                && "success".equals(response.getBody().get("type"));
    }

    /** Safely extract MSG91 error message from response body */
    private String extractMessage(ResponseEntity<Map<String, Object>> response) {
        if (response.getBody() == null)
            return "no response body";
        Object msg = response.getBody().get("message");
        return msg != null ? msg.toString() : "unknown error";
    }

    /**
     * Mask phone number for safe logging.
     * "9876543210" → "98XXXXX210" (first 2 + last 3 digits visible)
     */
    private String maskNumber(String number) {
        if (number == null || number.length() < 5)
            return "****";
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() < 5)
            return "****";
        return digits.substring(0, 2) + "XXXXX" + digits.substring(digits.length() - 3);
    }

    /**
     * Validate that the input is a recognizable Indian mobile number.
     * Accepts: "9876543210", "09876543210", "+919876543210", "919876543210"
     */
    private boolean isValidMobile(String number) {
        if (number == null || number.isBlank())
            return false;
        String cleaned = number.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("91") && cleaned.length() == 12)
            return true;
        if (cleaned.startsWith("0") && cleaned.length() == 11)
            return true;
        return cleaned.length() == 10;
    }

    /**
     * Normalize any Indian number format to "91XXXXXXXXXX".
     * Must only be called after isValidMobile() passes.
     */
    private String formatIndianNumber(String number) {
        number = number.replaceAll("[^0-9]", "");
        if (number.startsWith("91") && number.length() == 12)
            return number;
        if (number.startsWith("0"))
            number = number.substring(1);
        if (number.length() == 10)
            number = "91" + number;
        return number;
    }

    public boolean isEnabled() {
        return enabled;
    }
}