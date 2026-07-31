package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailResponseDto {
    private Long id;
    private Long userId;
    private String customerName;
    private String mobileNumber;
    private String village;
    private String pincode;
    private String emailId;
    private LocalDate customerCreatedDate;
    private BigDecimal dueAmount;
    private LocalDate dueDate;
    private LocalDate paymentDate;
}
