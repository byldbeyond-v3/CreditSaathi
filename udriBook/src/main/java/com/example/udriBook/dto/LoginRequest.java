package com.example.udriBook.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {
    private String phoneOrEmail;
    private String password;
}
