package com.example.udriBook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.udriBook.entity.Customer;
import com.example.udriBook.entity.CustomerDue;

@Repository
public interface CustomerDueRepository extends JpaRepository<CustomerDue, Long> {
    List<CustomerDue> findByCustomer(Customer customer);

    java.util.Optional<CustomerDue> findTopByCustomerOrderByCreatedAtDesc(Customer customer);
}
