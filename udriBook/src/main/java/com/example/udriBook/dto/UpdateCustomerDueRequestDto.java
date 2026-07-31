package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UpdateCustomerDueRequestDto {
    private Long customerId;
    private Long id;
    private BigDecimal dueAmount;
    private String description;
    private LocalDate paymentDate;
}
