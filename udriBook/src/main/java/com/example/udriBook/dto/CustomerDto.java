package com.example.udriBook.dto;

import java.time.LocalDate;

import com.example.udriBook.validation.ValidMobileNumber;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerDto - Data Transfer Object for Customer API requests
 * Contains validation annotations to ensure data integrity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {
    
    @NotBlank(message = "Customer name is required")
    private String customerName;
    
    @ValidMobileNumber(message = "Mobile number must be exactly 10 digits")
    private String mobileNumber;
    
    @NotBlank(message = "Village name is required")
    private String village;
    
    @NotBlank(message = "Pincode is required")
    private String pincode;
    
    
    private String emailId;
    
    @NotNull(message = "Customer created date is required")
    private LocalDate customerCreatedDate;
}
