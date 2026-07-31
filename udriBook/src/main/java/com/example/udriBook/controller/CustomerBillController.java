package com.example.udriBook.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.udriBook.dto.CustomerAttachmentDto;
import com.example.udriBook.dto.CustomerDueResponseDto;
import com.example.udriBook.dto.CustomerFilesDeletionResultDto;
import com.example.udriBook.entity.CustomerDue;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.service.CustomerService;
import com.example.udriBook.util.ApiResponse;
import com.example.udriBook.util.SecurityUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * CustomerBillController - REST API endpoints for customer bill record retrieval.
 * All endpoints require multi-user data isolation via SecurityUtil.
 */
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/customers/{customerId}")
public class CustomerBillController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SecurityUtil securityUtil;

    /**
     * GET API - Fetch all bill records for a specific customer.
     * Endpoint: GET /api/customers/{customerId}/bill-records
     *
     * @param customerId - ID of the customer
     * @return - Success response with list of bill records mapped to DTOs
     */
    @GetMapping("/bill-records")
    public ResponseEntity<?> getBillRecords(@PathVariable Long customerId) {
        log.info("Fetching bill records for customerId: {}", customerId);
        
        // Get current user for data isolation
        UserEntity currentUser = securityUtil.getCurrentUser();
        
        // Fetch bill records using service
        List<CustomerDue> billRecords = customerService.getBillRecords(customerId, currentUser);

        // Map entities to DTOs for production readiness
        List<CustomerDueResponseDto> responseList = billRecords.stream()
                .map(due -> new CustomerDueResponseDto(
                        due.getId(),
                        due.getDueAmount(),
                        due.getDueDate(),
                        due.getFilePath(),
                        due.getDescription(),
                        due.getPaymentDate(),
                        due.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), 
                "Bill records fetched successfully", 
                responseList));
    }

    /**
     * GET API - Fetch all attached files for a specific customer.
     * Endpoint: GET /api/customers/{customerId}/attachments
     *
     * @param customerId - ID of the customer
     * @return - Success response with list of attachments
     */
    @GetMapping("/attachments")
    public ResponseEntity<?> getCustomerAttachments(@PathVariable Long customerId) {
        log.info("Fetching attachments for customerId: {}", customerId);
        UserEntity currentUser = securityUtil.getCurrentUser();
        
        // Use the centralized service method to get all attachments
        List<CustomerAttachmentDto> attachments = customerService.getCustomerAttachments(customerId, currentUser);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(), 
                "Attachments fetched successfully", 
                attachments));
    }

    /**
     * @param customerId - ID of the customer whose files are to be deleted
     * @return - Success response containing deletion statistics
     */
    @DeleteMapping("/files")
    public ResponseEntity<?> deleteAllCustomerFiles(@PathVariable Long customerId) {
        log.info("DELETE /api/customers/{}/files received", customerId);

        UserEntity currentUser = securityUtil.getCurrentUser();

        CustomerFilesDeletionResultDto result = customerService.deleteAllCustomerFiles(customerId, currentUser);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                "All customer files deleted successfully",
                result));
    }
}
