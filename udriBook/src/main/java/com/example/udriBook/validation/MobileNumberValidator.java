package com.example.udriBook.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * MobileNumberValidator - Custom validator for Indian mobile numbers
 * Validates that mobile number is exactly 10 digits (0-9)
 * Pattern: ^[0-9]{10}$
 */
public class MobileNumberValidator implements ConstraintValidator<ValidMobileNumber, String> {
    
    // Pattern for 10-digit mobile numbers
    private static final String MOBILE_PATTERN = "^[0-9]{10}$";
    private static final Pattern COMPILED_PATTERN = Pattern.compile(MOBILE_PATTERN);
    
    /**
     * Initialize the validator
     * @param constraintAnnotation - The annotation instance
     */
    @Override
    public void initialize(ValidMobileNumber constraintAnnotation) {
        // No initialization needed for this validator
    }
    
    /**
     * Validate the mobile number
     * @param value - Mobile number value to validate
     * @param context - Constraint validator context
     * @return - true if valid (exactly 10 digits), false otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values - @NotNull/@NotBlank will handle null validation
        if (value == null) {
            return true;
        }
        
        // Trim whitespace and validate
        String trimmedValue = value.trim();
        
        // Check if the mobile number matches the pattern (exactly 10 digits)
        return COMPILED_PATTERN.matcher(trimmedValue).matches();
    }
}
