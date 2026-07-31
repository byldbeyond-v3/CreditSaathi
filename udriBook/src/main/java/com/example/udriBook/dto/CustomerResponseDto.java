package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerResponseDto - Data Transfer Object for Customer API responses
 * Contains only the fields needed for UI display
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponseDto {

    private Long id;
    private String customerName;
    private String mobileNumber;
    private BigDecimal dueAmount;
    private LocalDate dueDate;
}
