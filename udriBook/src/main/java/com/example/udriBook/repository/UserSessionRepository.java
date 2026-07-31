package com.example.udriBook.repository;

import com.example.udriBook.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    long countByActiveTrue();
    long countByUserIdAndActiveTrue(Long userId);

    Optional<UserSession> findByTokenAndActiveTrue(String token);

    Optional<UserSession> findByToken(String token);

    java.util.List<UserSession> findByUserIdAndActiveTrue(Long userId);
}
