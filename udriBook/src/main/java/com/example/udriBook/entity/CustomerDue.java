package com.example.udriBook.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "customer_dues")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private Long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_due_customer"))
    private Customer customer;

    @Column(name = "due_amount", nullable = false)
    private BigDecimal dueAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // Optional description for the bill

    @Column(name = "payment_date")
    private LocalDate paymentDate; // Date when payment was made

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
