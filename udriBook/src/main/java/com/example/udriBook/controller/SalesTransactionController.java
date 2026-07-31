package com.example.udriBook.controller;

import com.example.udriBook.dto.SalesTransactionDto.SalesTransactionRequest;
import com.example.udriBook.dto.SalesTransactionDto.SalesTransactionResponse;
import com.example.udriBook.entity.SalesTransaction;
import com.example.udriBook.service.SalesTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sales")
public class SalesTransactionController {

    private final SalesTransactionService service;

    public SalesTransactionController(SalesTransactionService service) {
        this.service = service;
    }

    private SalesTransactionResponse mapToResp(SalesTransaction t) {
        return new SalesTransactionResponse(
                t.getId(),
                t.getAmount(),
                t.getPaymentMode().name(),
                t.getTransactionDateTime(),
                t.getNotes(),
                t.getCreatedAt()
        );
    }

    /**
     * POST /api/sales
     * Creates a new sale transaction.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addSale(
            @RequestHeader("X-User-Identity") String userIdentity,
            @RequestBody SalesTransactionRequest request) {

        Map<String, Object> response = new HashMap<>();
        try {
            SalesTransaction saved = service.addSale(userIdentity, request);
            response.put("status", 200);
            response.put("message", "Sale recorded successfully");
            response.put("data", mapToResp(saved));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", 400);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", 500);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * GET /api/sales?date=today (or yyyy-MM-dd)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSales(
            @RequestHeader("X-User-Identity") String userIdentity,
            @RequestParam(required = false, defaultValue = "today") String date) {

        Map<String, Object> response = new HashMap<>();
        try {
            List<SalesTransaction> sales;
            if ("today".equalsIgnoreCase(date)) {
                sales = service.getTodaySales(userIdentity);
            } else {
                LocalDate specificDate = LocalDate.parse(date);
                sales = service.getSalesForDate(userIdentity, specificDate);
            }

            List<SalesTransactionResponse> dtos = sales.stream()
                    .map(this::mapToResp)
                    .collect(Collectors.toList());

            response.put("status", 200);
            response.put("message", "Success");
            response.put("data", dtos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", 500);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * GET /api/sales/trend?days=7
     */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestHeader("X-User-Identity") String userIdentity,
            @RequestParam(defaultValue = "7") int days) {

        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> trend = service.getDailyTrend(userIdentity, days);
            response.put("status", 200);
            response.put("message", "Success");
            response.put("data", trend);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", 500);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
