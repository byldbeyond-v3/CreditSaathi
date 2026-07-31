package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDueResponseDto {
    private Long id;
    private BigDecimal dueAmount;
    private LocalDate dueDate;
    private String filePath;
    private String description;
    private LocalDate paymentDate;
    private LocalDateTime createdAt;
}
