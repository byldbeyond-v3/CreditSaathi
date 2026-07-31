package com.example.udriBook.controller;

import com.example.udriBook.dto.FeedbackDto;
import com.example.udriBook.entity.Feedback;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.service.FeedbackService;
import com.example.udriBook.util.ApiResponse;
import com.example.udriBook.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // In production, replace with specific origins
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final SecurityUtil securityUtil;

    /**
     * POST API - Submit user feedback
     * Endpoint: POST /api/feedback
     * 
     * @param feedbackDto - Feedback details (rating, comments)
     * @return - ApiResponse with success message and created feedback data
     */
    @PostMapping
    public ResponseEntity<?> submitFeedback(@Valid @RequestBody FeedbackDto feedbackDto) {
        try {
            // Get current user for multi-user isolation and attribution
            UserEntity currentUser = securityUtil.getCurrentUser();
            
            log.info("Feedback submission request from user: {}", currentUser.getPhoneNumber());

            Feedback savedFeedback = feedbackService.saveFeedback(feedbackDto, currentUser);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                    HttpStatus.CREATED.value(),
                    "Feedback submitted successfully",
                    savedFeedback
                )
            );

        } catch (com.example.udriBook.exception.CustomException e) {
            log.error("Authentication/User error during feedback submission: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error during feedback submission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "An unexpected error occurred while processing your feedback"
                )
            );
        }
    }
}
