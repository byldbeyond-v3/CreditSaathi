package com.example.udriBook.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, length = 30)
    private String transactionId; // e.g. CRD-20240323-A1B2C3D4 (Credit) or DBT-20240323-A1B2C3D4 (Debit)

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transaction_customer"))
    private Customer customer;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount; // The change in amount (positive for dues, negative for payments)

    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Column(name = "balance_after")
    private BigDecimal balanceAfter; // Total due amount after this transaction

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath; // Comma-separated uploaded file names attached to this transaction (null if none)

    public enum TransactionType {
        DUE_ADDED, // From /add
        BILL_UPDATED, // From /save-bill/update
        PAYMENT_RECEIVED // From /save-payment/update
    }
}
