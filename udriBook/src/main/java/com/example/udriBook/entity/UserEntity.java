package com.example.udriBook.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_type")
    private String storeType;

    @Column(unique = true, name = "phone_number")
    private String phoneNumber;

    @Column(unique = true, name = "email_id")
    private String emailId;

    @Column(name = "village_name")
    private String villageName;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "image_url")
    private String imageUrl;
}
