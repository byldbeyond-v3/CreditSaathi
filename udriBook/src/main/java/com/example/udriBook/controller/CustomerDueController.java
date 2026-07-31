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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.udriBook.dto.CustomerDueResponseDto;
import com.example.udriBook.dto.CustomerTransactionResponseDto;
import com.example.udriBook.dto.UpdateCustomerDueRequestDto;
import com.example.udriBook.entity.CustomerDue;
import com.example.udriBook.entity.CustomerTransaction;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.exception.CustomerNotFoundException;
import com.example.udriBook.service.CustomerService;
import com.example.udriBook.util.ApiResponse;
import com.example.udriBook.util.SecurityUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/customers/dues")
public class CustomerDueController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SecurityUtil securityUtil;

    /**
     * POST API - Add customer due with file
     * Endpoint: POST /api/customers/dues/add
     * 
     * @param customerId   - ID of the customer
     * @param dueAmount    - Amount due
     * @param dueDate      - Due date
     * @param description  - Optional description
     * @param file         - File (JPEG, JPG, PDF)
     * @param userIdentity - User's phone or email
     * @return - Success response
     */
    @PostMapping(value = "/add", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addCustomerDue(
            @RequestParam("customerId") Long customerId,
            @RequestParam("dueAmount") java.math.BigDecimal dueAmount,
            @RequestParam("dueDate") java.time.LocalDate dueDate,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "files", required = false) java.util.List<MultipartFile> files) {
        
        UserEntity currentUser = securityUtil.getCurrentUser();

        CustomerDue savedDue = customerService.addCustomerDue(customerId, dueAmount,
                dueDate, description, files, currentUser);

        CustomerDueResponseDto responseDto = new CustomerDueResponseDto(
                savedDue.getId(),
                savedDue.getDueAmount(),
                savedDue.getDueDate(),
                savedDue.getFilePath(),
                savedDue.getDescription(),
                savedDue.getPaymentDate(),
                savedDue.getCreatedAt());

        return buildSuccessResponse(HttpStatus.CREATED.value(), "Customer due updated successfully", responseDto);
    }

    /**
     * PUT API - Update customer bill (Add amount and update due date)
     * Endpoint: PUT /api/customers/dues/save-bill/update
     * 
     * @param customerId  - ID of the customer
     * @param dueAmount   - Amount to add
     * @param dueDate     - New due date
     * @param description - Optional description
     * @param files       - Optional files
     * @return - Success response
     */
    @PutMapping(value = "/save-bill/update", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveBillUpdate(
            @RequestParam("customerId") Long customerId,
            @RequestParam("dueAmount") java.math.BigDecimal dueAmount,
            @RequestParam("dueDate") String dueDateStr,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "files", required = false) java.util.List<MultipartFile> files) {
        
        UserEntity currentUser = securityUtil.getCurrentUser();
        java.time.LocalDate dueDate = java.time.LocalDate.parse(dueDateStr);

        CustomerDue updatedDue = customerService.saveBillUpdate(
                customerId,
                dueAmount,
                dueDate,
                description,
                id,
                files,
                currentUser);

        CustomerDueResponseDto responseDto = new CustomerDueResponseDto(
                updatedDue.getId(),
                updatedDue.getDueAmount(),
                updatedDue.getDueDate(),
                updatedDue.getFilePath(),
                updatedDue.getDescription(),
                updatedDue.getPaymentDate(),
                updatedDue.getCreatedAt());

        return buildSuccessResponse(HttpStatus.OK.value(), "Bill updated successfully", responseDto);
    }

    /**
     * POST API - Save a new customer payment
     * Endpoint: POST /api/customers/dues/save-payment
     * 
     * @param request - Request body containing payment details
     * @return - Success response
     */
    @PostMapping("/save-payment")
    public ResponseEntity<?> savePayment(
            @RequestBody UpdateCustomerDueRequestDto request) {
        
        UserEntity currentUser = securityUtil.getCurrentUser();

        CustomerDue savedDue = customerService.savePayment(
                request.getCustomerId(),
                request.getDueAmount(),
                request.getDescription(),
                request.getPaymentDate(),
                currentUser);

        CustomerDueResponseDto responseDto = new CustomerDueResponseDto(
                savedDue.getId(),
                savedDue.getDueAmount(),
                savedDue.getDueDate(),
                savedDue.getFilePath(),
                savedDue.getDescription(),
                savedDue.getPaymentDate(),
                savedDue.getCreatedAt());

        return buildSuccessResponse(HttpStatus.CREATED.value(), "Payment saved successfully", responseDto);
    }

    /**
     * PUT API - Update an existing transaction
     * Endpoint: PUT /api/customers/dues/save-payment/update
     * 
     * @param request - Request body containing transaction ID and updated details
     * @return - Success response
     */
    @PutMapping("/save-payment/update")
    public ResponseEntity<?> updateTransaction(
            @RequestBody UpdateCustomerDueRequestDto request) {
        if (request.getId() == null) {
            return buildSuccessResponse(HttpStatus.BAD_REQUEST.value(), "Transaction ID is required for updates", null);
        }
        
        UserEntity currentUser = securityUtil.getCurrentUser();

        CustomerDue updatedDue = customerService.updateTransaction(
                request.getId(),
                request.getDueAmount(),
                request.getDescription(),
                request.getPaymentDate(),
                currentUser);

        CustomerDueResponseDto responseDto = new CustomerDueResponseDto(
                updatedDue.getId(),
                updatedDue.getDueAmount(),
                updatedDue.getDueDate(),
                updatedDue.getFilePath(),
                updatedDue.getDescription(),
                updatedDue.getPaymentDate(),
                updatedDue.getCreatedAt());

        return buildSuccessResponse(HttpStatus.OK.value(), "Transaction updated successfully", responseDto);
    }

    /**
     * GET API - Fetch transaction history for a customer
     * Endpoint: GET /api/customers/dues/history/{customerId}
     *
     * @param customerId - ID of the customer
     * @return - List of transactions
     */
    @GetMapping("/history/{customerId}")
    public ResponseEntity<?> getTransactionHistory(@PathVariable Long customerId) {
        UserEntity currentUser = securityUtil.getCurrentUser();
        List<CustomerTransaction> history = customerService.getTransactionHistory(customerId, currentUser);

        List<CustomerTransactionResponseDto> responseDtos = history.stream()
                .map(t -> new CustomerTransactionResponseDto(
                        t.getId(),
                        t.getTransactionId(),
                        t.getCustomer().getCustomerName(),
                        t.getAmount(),
                        t.getTransactionType().name(),
                        t.getDescription(),
                        t.getTransactionDate(),
                        t.getBalanceAfter(),
                        t.getFilePath()))
                .collect(Collectors.toList());

        return buildSuccessResponse(HttpStatus.OK.value(), "History fetched successfully", responseDtos);
    }

    /**
     *   @param recordId - CustomerTransaction.id of the bill to remove
     * @return 200 OK on success  |  404 Not Found if recordId is unknown or not owned by caller
     */
    @DeleteMapping("/delete/{recordId}")
    public ResponseEntity<?> deleteBillRecord(@PathVariable Long recordId) {
        log.info("DELETE /api/customers/dues/delete/{} received", recordId);

        UserEntity currentUser = securityUtil.getCurrentUser();

        try {
            customerService.deleteDueTransaction(recordId, currentUser);
            return buildSuccessResponse(
                    HttpStatus.OK.value(),
                    "Bill record deleted successfully",
                    null);
        } catch (CustomerNotFoundException ex) {
            // Record does not exist OR belongs to a different user—return 404
            log.warn("Bill record not found or access denied: recordId={}, userId={}",
                    recordId, currentUser.getId());
            ApiResponse<?> notFound = ApiResponse.error(
                    HttpStatus.NOT_FOUND.value(),
                    "Bill record not found or does not belong to your account");
            return new ResponseEntity<>(notFound, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * @param recordId - CustomerTransaction.id that owns the attachment
     * @param fileName - Exact stored file name to remove (e.g. "a3f1bc-uuid.pdf")
     * @return 200 OK on success
     */
    @DeleteMapping("/{recordId}/attachment")
    public ResponseEntity<?> deleteTransactionAttachment(
            @PathVariable Long recordId,
            @RequestParam("fileName") String fileName) {

        log.info("DELETE /api/customers/dues/{}/attachment?fileName={} received", recordId, fileName);

        UserEntity currentUser = securityUtil.getCurrentUser();

        try {
            customerService.deleteTransactionAttachment(recordId, fileName, currentUser);
            return buildSuccessResponse(
                    HttpStatus.OK.value(),
                    "Attachment '" + fileName + "' removed successfully",
                    null);
        } catch (CustomerNotFoundException ex) {
            log.warn("Transaction not found or access denied: recordId={}, userId={}",
                    recordId, currentUser.getId());
            ApiResponse<?> notFound = ApiResponse.error(
                    HttpStatus.NOT_FOUND.value(),
                    "Transaction not found or does not belong to your account");
            return new ResponseEntity<>(notFound, HttpStatus.NOT_FOUND);
        }
    }

    private <T> ResponseEntity<?> buildSuccessResponse(int status, String message, T data) {
        ApiResponse<T> response = ApiResponse.success(status, message, data);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }
}
