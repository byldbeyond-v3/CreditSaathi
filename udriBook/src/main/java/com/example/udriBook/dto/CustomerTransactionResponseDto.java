package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTransactionResponseDto {
    private Long id;
    private String transactionId;
    private String customerName;       // Customer display name
    private BigDecimal amount;
    private String type;               // "PAYMENT" or "BILL" — simplified for client
    private String description;        // Payment mode (e.g. "Cash", "UPI") or notes
    private LocalDateTime date;        // transactionDate mapped to 'date' for client
    private BigDecimal balanceAfter;
    private String filePath;           // Comma-separated file names attached to this transaction (null if none)
}
