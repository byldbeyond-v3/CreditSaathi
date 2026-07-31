package com.example.udriBook.repository;

import com.example.udriBook.entity.SalesTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, Long> {

    /**
     * Fetch all sales for a user within a date-time range (for "today" queries).
     */
    List<SalesTransaction> findByUserIdentityAndTransactionDateTimeBetweenOrderByTransactionDateTimeDesc(
            String userIdentity,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Daily aggregation for the trend chart — returns (date_str, total) pairs.
     * Uses DATE() function which is standard across MySQL and PostgreSQL.
     */
    @Query("""
        SELECT CAST(s.transactionDateTime AS date) AS saleDate,
               SUM(s.amount) AS totalAmount
        FROM SalesTransaction s
        WHERE s.userIdentity = :userIdentity
          AND s.transactionDateTime >= :since
        GROUP BY CAST(s.transactionDateTime AS date)
        ORDER BY saleDate ASC
    """)
    List<Object[]> findDailyTotals(
            @Param("userIdentity") String userIdentity,
            @Param("since") LocalDateTime since
    );
}
