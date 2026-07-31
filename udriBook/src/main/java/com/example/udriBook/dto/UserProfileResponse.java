package com.example.udriBook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private String username;
    private String shopName;
    private String phone;
    private String storeType;
    private String email;
    private String imageUrl;
}
