package com.example.udriBook.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.udriBook.entity.Customer;
import com.example.udriBook.entity.UserEntity;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    /**
     * Find customer by mobile number and user (ensures user can only access their own customers)
     * @param mobileNumber - Customer mobile number
     * @param user - User entity
     * @return - Optional containing customer if found
     */
    Optional<Customer> findByMobileNumberAndUser(String mobileNumber, UserEntity user);
    
    /**
     * Find customers by name for a specific user (case-insensitive, partial match)
     * @param customerName - Customer name or partial name
     * @param user - User entity
     * @return - List of customers matching the name for this user
     */
    List<Customer> findByCustomerNameContainingIgnoreCaseAndUser(String customerName, UserEntity user);
    
    
    /**
     * Get all customers for a specific user, ordered by latest added first with pagination
     * @param user - User entity
     * @param pageable - Pagination information
     * @return - Page of customers belonging to the user
     */
    org.springframework.data.domain.Page<Customer> findByUserOrderByIdDesc(UserEntity user, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Get a customer by ID and user (ensures user can only access their own customers)
     * @param id - Customer ID
     * @param user - User entity
     * @return - Optional containing customer if found and belongs to user
     */
    Optional<Customer> findByIdAndUser(Long id, UserEntity user);
    
    /**
     * Check if a customer exists for a specific user
     * @param id - Customer ID
     * @param user - User entity
     * @return - true if customer exists and belongs to user
     */
    boolean existsByIdAndUser(Long id, UserEntity user);
}

