package com.example.udriBook.service;

import com.example.udriBook.dto.SalesTransactionDto.SalesTransactionRequest;
import com.example.udriBook.entity.SalesTransaction;
import com.example.udriBook.entity.SalesTransaction.PaymentMode;
import com.example.udriBook.repository.SalesTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SalesTransactionService {

    private final SalesTransactionRepository repo;

    public SalesTransactionService(SalesTransactionRepository repo) {
        this.repo = repo;
    }

    // ── Add Sale ──────────────────────────────────────────────────────────────

    @Transactional
    public SalesTransaction addSale(String userIdentity, SalesTransactionRequest req) {
        // Validate amount
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Validate payment mode
        PaymentMode mode;
        try {
            mode = PaymentMode.valueOf(req.getPaymentMode().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payment mode: " + req.getPaymentMode()
                    + ". Must be CASH, UPI, or CARD");
        }

        LocalDateTime txnTime = req.getTransactionDateTime() != null
                ? req.getTransactionDateTime()
                : LocalDateTime.now();

        SalesTransaction sale = new SalesTransaction(
                userIdentity,
                req.getAmount(),
                mode,
                txnTime,
                req.getNotes()
        );

        return repo.save(sale);
    }

    // ── Get Today's Sales ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SalesTransaction> getTodaySales(String userIdentity) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return repo.findByUserIdentityAndTransactionDateTimeBetweenOrderByTransactionDateTimeDesc(
                userIdentity, startOfDay, endOfDay
        );
    }

    // ── Get Sales For A Specific Date ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SalesTransaction> getSalesForDate(String userIdentity, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return repo.findByUserIdentityAndTransactionDateTimeBetweenOrderByTransactionDateTimeDesc(
                userIdentity, start, end
        );
    }

    // ── Get Daily Trend (last N days) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyTrend(String userIdentity, int days) {
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Object[]> raw = repo.findDailyTotals(userIdentity, since);

        // Build a date-keyed map from the raw DB results
        Map<String, BigDecimal> dbMap = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Object[] row : raw) {
            String dateStr = row[0].toString().substring(0, 10); // normalize to yyyy-MM-dd
            BigDecimal total = new BigDecimal(row[1].toString());
            dbMap.put(dateStr, total);
        }

        // Fill in zeros for any missing days
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String dateStr = LocalDate.now().minusDays(i).format(fmt);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", dateStr);
            entry.put("totalAmount", dbMap.getOrDefault(dateStr, BigDecimal.ZERO));
            trend.add(entry);
        }
        return trend;
    }
}
