package com.example.udriBook.repository;

import com.example.udriBook.entity.UserProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileImageRepository extends JpaRepository<UserProfileImage, Long> {
    Optional<UserProfileImage> findByUserId(Long userId);
}
