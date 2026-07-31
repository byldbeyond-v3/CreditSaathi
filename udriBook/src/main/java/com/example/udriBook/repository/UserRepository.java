package com.example.udriBook.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.udriBook.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    @Query("""
        SELECT u FROM UserEntity u
        WHERE u.phoneNumber = :value OR u.emailId = :value
    """)
    Optional<UserEntity> findByPhoneOrEmail(@Param("value") String value);

    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmailId(String emailId);
}
