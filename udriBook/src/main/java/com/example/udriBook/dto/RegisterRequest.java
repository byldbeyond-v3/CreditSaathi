package com.example.udriBook.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterRequest {
    private String ownerName;
    private String storeName;
    private String storeType;
    private String phoneNumber;
    private String emailId;
    private String villageName;
    private String pincode;
    private String password;
}
