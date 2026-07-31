package com.example.udriBook.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.exception.CustomException;
import com.example.udriBook.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityUtil - Utility class for security-related operations
 * Provides methods to retrieve the currently authenticated user
 */
@Component
public class SecurityUtil {

    @Autowired
    private UserRepository userRepository;

    /**
     * Get the current authenticated user from Spring Security context
     * 
     * @return - UserEntity of the authenticated user
     * @throws CustomException if user not found or authenticated
     */
    public UserEntity getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String phoneOrEmail;

        if (principal instanceof String) {
            phoneOrEmail = (String) principal;
        } else {
            throw new CustomException("User not authenticated");
        }

        return userRepository.findByPhoneOrEmail(phoneOrEmail)
                .orElseThrow(() -> new CustomException("User not found: " + phoneOrEmail));
    }

    /**
     * Get current user by ID
     * 
     * @param userId - User ID
     * @return - UserEntity if found
     * @throws CustomException if user not found
     */
    public UserEntity getCurrentUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found with ID: " + userId));
    }

    /**
     * Get current user by phone number or email
     * 
     * @param phoneOrEmail - Phone number or email
     * @return - UserEntity if found
     * @throws CustomException if user not found
     */
    public UserEntity getCurrentUserByPhoneOrEmail(String phoneOrEmail) {
        return userRepository.findByPhoneOrEmail(phoneOrEmail)
                .orElseThrow(() -> new CustomException("User not found with phone/email: " + phoneOrEmail));
    }
}
