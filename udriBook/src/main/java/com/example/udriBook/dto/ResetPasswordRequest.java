package com.example.udriBook.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String phoneOrEmail;
    private String otp;
    private String newPassword;
}
