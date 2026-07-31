package com.example.udriBook.entity;

import java.time.LocalDate;

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
@Table(name = "u_customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private Long version;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_customer_user"))
    private UserEntity user;
    
    @Column(name = "customer_name", nullable = false)
    private String customerName;
    
    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;
    
    @Column(name = "village_name", nullable = false)
    private String village;
    
    @Column(name = "pincode", nullable = false)
    private String pincode;

    @Column(name = "email_id")
    private String emailId;
    
    @Column(name = "customer_created_date", nullable = false)
    private LocalDate customerCreatedDate;

    /**
     * Whether this customer's mobile number has been verified via OTP.
     * Defaults to false. Set to true after successful OTP verification.
     * Stored as TINYINT(1) in MySQL (0 = false, 1 = true).
     */
    @Column(name = "mobile_verified", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean mobileVerified = false;

}
