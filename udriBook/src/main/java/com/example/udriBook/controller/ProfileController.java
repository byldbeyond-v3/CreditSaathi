package com.example.udriBook.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.udriBook.util.ApiResponse;

import com.example.udriBook.dto.UserProfileResponse;
import com.example.udriBook.dto.UserProfileUpdate;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.service.UserProfileService;
import com.example.udriBook.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/users/profile")
@RequiredArgsConstructor
public class ProfileController {

    @Autowired
    private SecurityUtil securityUtil;

    private final UserProfileService profileService;


    // GET profile of the authenticated user
    @GetMapping
    public UserProfileResponse getProfile() {
        UserEntity currentUser = securityUtil.getCurrentUser();
        return profileService.getProfile(currentUser);
    }

    // UPDATE profile
    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody UserProfileUpdate request) {
        UserEntity currentUser = securityUtil.getCurrentUser();
        profileService.updateProfile(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Profile updated successfully", null));
    }

    // VERIFY password
    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestParam String password) {
        UserEntity currentUser = securityUtil.getCurrentUser();
        boolean isMatched = profileService.verifyPassword(currentUser, password);
        if (isMatched) {
            return ResponseEntity.ok(ApiResponse.success(200, "Password matched", null));
        } else {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Invalid password"));
        }
    }

    // CHANGE password
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String newPassword) {
        UserEntity currentUser = securityUtil.getCurrentUser();
        profileService.changePassword(currentUser, newPassword);
        return ResponseEntity.ok(ApiResponse.success(200, "Password changed successfully", null));
    }

    // UPLOAD profile image
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam("image") MultipartFile file) throws java.io.IOException {
        UserEntity currentUser = securityUtil.getCurrentUser();
        String imagePath = profileService.uploadProfileImage(currentUser, file);
        return ResponseEntity.ok(ApiResponse.success(200, "Image uploaded successfully", imagePath));
    }
}
