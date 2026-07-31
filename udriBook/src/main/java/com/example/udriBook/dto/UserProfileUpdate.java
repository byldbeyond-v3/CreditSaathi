package com.example.udriBook.dto;

import lombok.Data;

@Data
public class UserProfileUpdate {
    private String username; // editable username
    private String shopName;
    private String phone;
    private String storeType;
    private String email;
    private String imageUrl;
}
