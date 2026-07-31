package com.example.udriBook.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.udriBook.entity.Customer;
import com.example.udriBook.entity.CustomerTransaction;

@Repository
public interface CustomerTransactionRepository extends JpaRepository<CustomerTransaction, Long> {

    List<CustomerTransaction> findByCustomerOrderByTransactionDateDesc(Customer customer);

    /** All transactions for a specific user, ordered by date desc */
    @Query("SELECT t FROM CustomerTransaction t " +
           "WHERE (t.customer.user.phoneNumber = :identity OR t.customer.user.emailId = :identity) " +
           "ORDER BY t.transactionDate DESC")
    List<CustomerTransaction> findByUsername(@Param("identity") String identity);

    /** Transactions for a user within a date range, ordered by date desc */
    @Query("SELECT t FROM CustomerTransaction t " +
           "WHERE (t.customer.user.phoneNumber = :identity OR t.customer.user.emailId = :identity) " +
           "AND t.transactionDate >= :from " +
           "AND t.transactionDate <= :to " +
           "ORDER BY t.transactionDate DESC")
    List<CustomerTransaction> findByUsernameAndDateRange(
            @Param("identity") String identity,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Most recent N transactions for a user (used for Recent Activity) */
    @Query("SELECT t FROM CustomerTransaction t " +
           "WHERE (t.customer.user.phoneNumber = :identity OR t.customer.user.emailId = :identity) " +
           "ORDER BY t.transactionDate DESC")
    List<CustomerTransaction> findTopByUsername(@Param("identity") String identity, Pageable pageable);

    /** Most recent N transactions for a user within a date range */
    @Query("SELECT t FROM CustomerTransaction t " +
           "WHERE (t.customer.user.phoneNumber = :identity OR t.customer.user.emailId = :identity) " +
           "AND t.transactionDate >= :from " +
           "AND t.transactionDate <= :to " +
           "ORDER BY t.transactionDate DESC")
    List<CustomerTransaction> findTopByUsernameAndDateRange(
            @Param("identity") String identity,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    
    List<CustomerTransaction> findByCustomerAndTransactionDateAfterOrderByTransactionDateAsc(
            Customer customer, LocalDateTime transactionDate);
}
