package com.example.udriBook.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.udriBook.dto.UserProfileResponse;
import com.example.udriBook.dto.UserProfileUpdate;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.entity.UserProfileImage;
import com.example.udriBook.repository.UserProfileImageRepository;
import com.example.udriBook.repository.UserRepository;

@Service
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileImageRepository userProfileImageRepository;

    @Autowired
    private com.example.udriBook.util.PasswordUtil passwordUtil;

    private final String uploadDir = "uploads/profiles";

    public UserProfileResponse getProfile(UserEntity user) {
        return new UserProfileResponse(
                user.getOwnerName(),
                user.getStoreName(),
                user.getPhoneNumber(),
                user.getStoreType(),
                user.getEmailId(),
                user.getImageUrl());
    }

    // UPDATE profile
    public void updateProfile(UserEntity currentUser, UserProfileUpdate request) {
        // Only update fields that are provided (not null)
        if (request.getUsername() != null) {
            currentUser.setOwnerName(request.getUsername());
        }
        if (request.getShopName() != null) {
            currentUser.setStoreName(request.getShopName());
        }
        if (request.getPhone() != null) {
            currentUser.setPhoneNumber(request.getPhone());
        }
        if (request.getStoreType() != null) {
            currentUser.setStoreType(request.getStoreType());
        }
        if (request.getImageUrl() != null) {
            currentUser.setImageUrl(request.getImageUrl());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // Optional: Check if the new email is already taken by another user
            userRepository.findByPhoneOrEmail(request.getEmail())
                    .filter(u -> !u.getId().equals(currentUser.getId()))
                    .ifPresent(u -> {
                        throw new RuntimeException("Email already registered by another user");
                    });
            currentUser.setEmailId(request.getEmail());
        }

        // Save using UserRepository
        userRepository.save(currentUser);
    }

    // VERIFY password
    public boolean verifyPassword(UserEntity user, String currentPassword) {
        return passwordUtil.matchPassword(currentPassword, user.getPasswordHash());
    }

    // CHANGE password
    public void changePassword(UserEntity user, String newPassword) {
        user.setPasswordHash(passwordUtil.hashPassword(newPassword));
        userRepository.save(user);
    }

    // UPLOAD profile image
    public String uploadProfileImage(UserEntity user, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        // 1. Validate file type (Both Content-Type and Extension)
        List<String> allowedTypes = Arrays.asList("image/jpeg", "image/jpg", "image/png", "application/octet-stream");
        List<String> allowedExtensions = Arrays.asList(".jpg", ".jpeg", ".png");

        boolean isValidType = contentType != null && allowedTypes.contains(contentType.toLowerCase());
        boolean isValidExtension = allowedExtensions.contains(extension);

        if (!isValidExtension || !isValidType) {
            throw new RuntimeException("Invalid file type. Only JPG, JPEG, and PNG are allowed. Detected type: "
                    + contentType + ", extension: " + extension);
        }

        // 2. Validate file size (5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException(
                    "File size exceeds 5MB limit. Current size: " + (file.getSize() / 1024 / 1024) + "MB");
        }

        // 3. Generate unique filename
        String fileName = UUID.randomUUID().toString() + extension;

        // 4. Create directory if not exists
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }

        // 5. Save to file system
        Path targetLocation = path.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        String imagePath = uploadDir + "/" + fileName;

        // 6. Update UserProfileImage table
        UserProfileImage profileImage = userProfileImageRepository.findByUserId(user.getId())
                .orElse(new UserProfileImage());
        profileImage.setUserId(user.getId());
        profileImage.setImageurly(imagePath);
        userProfileImageRepository.save(profileImage);

        // 7. Update UserEntity
        user.setImageUrl(imagePath);
        userRepository.save(user);

        return imagePath;
    }
}
