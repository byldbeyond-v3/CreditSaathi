package com.example.udriBook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SalesTransactionDto {

    // ─── Request DTO ──────────────────────────────────────────────────────────────

    /**
     * Payload for POST /api/sales — records a new sale transaction.
     */
    public static class SalesTransactionRequest {

        /** Required. Must be > 0 */
        private BigDecimal amount;

        /** Required. One of: CASH | UPI | CARD */
        private String paymentMode;

        /** The actual sale time (sent in ISO-8601, UTC). Defaults to now if null. */
        private LocalDateTime transactionDateTime;

        /** Optional sale description. */
        private String notes;

        // ── Getters & Setters ───────────────────────────────────────────────────

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getPaymentMode() { return paymentMode; }
        public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

        public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
        public void setTransactionDateTime(LocalDateTime transactionDateTime) {
            this.transactionDateTime = transactionDateTime;
        }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }


    // ─── Response DTO ─────────────────────────────────────────────────────────────

    /**
     * Response body for a single sale transaction — returned after create or in list.
     */
    public static class SalesTransactionResponse {

        private Long id;
        private BigDecimal amount;
        private String paymentMode;
        private LocalDateTime transactionDateTime;
        private String notes;
        private LocalDateTime createdAt;

        public SalesTransactionResponse() {}

        public SalesTransactionResponse(Long id, BigDecimal amount, String paymentMode,
                                        LocalDateTime transactionDateTime,
                                        String notes, LocalDateTime createdAt) {
            this.id = id;
            this.amount = amount;
            this.paymentMode = paymentMode;
            this.transactionDateTime = transactionDateTime;
            this.notes = notes;
            this.createdAt = createdAt;
        }

        public Long getId() { return id; }
        public BigDecimal getAmount() { return amount; }
        public String getPaymentMode() { return paymentMode; }
        public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
        public String getNotes() { return notes; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }


    // ─── Trend DTO ────────────────────────────────────────────────────────────────

    /**
     * One entry in the 7-day chart: a date + total sales amount.
     */
    public static class DailySaleAggregate {

        private String date;      // yyyy-MM-dd
        private BigDecimal totalAmount;

        public DailySaleAggregate(String date, BigDecimal totalAmount) {
            this.date = date;
            this.totalAmount = totalAmount;
        }

        public String getDate() { return date; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }
}
