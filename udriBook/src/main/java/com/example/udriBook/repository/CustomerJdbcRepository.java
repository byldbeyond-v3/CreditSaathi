package com.example.udriBook.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long getNumberOfCustomers(Long userId) {
        String sql = "SELECT COUNT(*) FROM u_customers WHERE user_id = ?";
        return jdbcTemplate.query(
            sql,
            new Object[]{userId},
            rs -> rs.next() ? rs.getLong(1) : 0L
        );
    }
}
