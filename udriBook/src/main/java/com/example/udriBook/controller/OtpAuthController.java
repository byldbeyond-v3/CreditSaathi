package com.example.udriBook.controller;

import com.example.udriBook.dto.GenerateOtpRequest;
import com.example.udriBook.dto.OtpVerifyRequest;
import com.example.udriBook.dto.OtpVerifyResponse;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.exception.OtpException;
import com.example.udriBook.repository.UserRepository;
import com.example.udriBook.service.SecureOtpService;
import com.example.udriBook.util.ApiResponse;
import com.example.udriBook.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;


@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OtpAuthController {

    private final SecureOtpService secureOtpService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

  
    private void mockSendOtp(String identifier, String otp) {
        log.info("===========================================");
        log.info("  [MOCK OTP SENDER]");
        log.info("  To      : {}", maskIdentifier(identifier));
        log.info("  OTP     : {} (send this via SMS/Email)", otp);
        log.info("  Expires : 5 minutes");
        log.info("===========================================");
        
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<?> generateOtp(@Valid @RequestBody GenerateOtpRequest request) {
        try {
            // Delegate OTP creation + Redis storage to service
            String otp = secureOtpService.generateOtp(request.getPhoneOrEmail());

            // Mock send (replace with real SMS/Email in production)
            mockSendOtp(request.getPhoneOrEmail(), otp);

            ApiResponse<?> response = ApiResponse.success(
                HttpStatus.OK.value(),
                "OTP sent successfully. Valid for 5 minutes.",
                null
            );
            return ResponseEntity.ok(response);

        } catch (OtpException ex) {
            // 429 Too Many Requests for rate limit, 400 for validation
            ApiResponse<?> response = ApiResponse.error(ex.getStatus().value(), ex.getMessage());
            return new ResponseEntity<>(response, ex.getStatus());
        }
    }

    
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        try {
            // Verify OTP — throws OtpException on failure
            secureOtpService.verifyOtp(request.getPhoneOrEmail(), request.getOtp());

           
            Optional<UserEntity> userOpt = userRepository.findByPhoneOrEmail(request.getPhoneOrEmail());

            String username;
            Long userId;

            if (userOpt.isPresent()) {
                UserEntity user = userOpt.get();
                username = user.getPhoneNumber() != null ? user.getPhoneNumber() : user.getEmailId();
                userId   = user.getId();
            } else {
                // New user — token serves as a "verified identity" for registration
                username = request.getPhoneOrEmail();
                userId   = -1L;
            }

            String accessToken = jwtUtil.generateAccessToken(username, userId);

            OtpVerifyResponse responseBody = OtpVerifyResponse.success(accessToken, username);
            ApiResponse<OtpVerifyResponse> response = ApiResponse.success(
                HttpStatus.OK.value(),
                "OTP verified successfully",
                responseBody
            );
            return ResponseEntity.ok(response);

        } catch (OtpException ex) {
            ApiResponse<?> response = ApiResponse.error(ex.getStatus().value(), ex.getMessage());
            return new ResponseEntity<>(response, ex.getStatus());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 4) return "****";
        if (identifier.contains("@")) {
            int at = identifier.indexOf('@');
            return identifier.substring(0, Math.min(2, at)) + "***" + identifier.substring(at);
        }
        return identifier.substring(0, 2) + "*****" + identifier.substring(identifier.length() - 3);
    }
}
