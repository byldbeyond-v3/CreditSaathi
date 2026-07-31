package com.example.udriBook.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a direct cash/UPI/card sale recorded by the business owner.
 * This is distinct from credit billing — a Sale is an immediate, paid transaction.
 */
@Entity
@Table(name = "sales_transaction", indexes = {
    @Index(name = "idx_sales_user_date", columnList = "userIdentity, transactionDateTime")
})
public class SalesTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matches user_identity (username/phone) from the auth system. */
    @Column(nullable = false, length = 100)
    private String userIdentity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMode paymentMode;

    /** The actual time the sale occurred (user-provided, stored as UTC). */
    @Column(nullable = false)
    private LocalDateTime transactionDateTime;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum PaymentMode {
        CASH, UPI, CARD
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public SalesTransaction() {}

    public SalesTransaction(String userIdentity, BigDecimal amount,
                            PaymentMode paymentMode,
                            LocalDateTime transactionDateTime,
                            String notes) {
        this.userIdentity = userIdentity;
        this.amount = amount;
        this.paymentMode = paymentMode;
        this.transactionDateTime = transactionDateTime;
        this.notes = notes;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getUserIdentity() { return userIdentity; }
    public void setUserIdentity(String userIdentity) { this.userIdentity = userIdentity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
    public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
    public void setTransactionDateTime(LocalDateTime transactionDateTime) { this.transactionDateTime = transactionDateTime; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
