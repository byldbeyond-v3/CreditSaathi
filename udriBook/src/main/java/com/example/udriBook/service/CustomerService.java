package com.example.udriBook.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.udriBook.dto.CustomerAttachmentDto;
import com.example.udriBook.dto.CustomerFilesDeletionResultDto;
import com.example.udriBook.dto.CustomerDto;
import com.example.udriBook.entity.Customer;
import com.example.udriBook.entity.CustomerDue;
import com.example.udriBook.entity.CustomerTransaction;
import com.example.udriBook.entity.CustomerTransaction.TransactionType;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.exception.CustomException;
import com.example.udriBook.exception.CustomerNotFoundException;
import com.example.udriBook.repository.CustomerJdbcRepository;
import com.example.udriBook.repository.CustomerRepository;
import com.example.udriBook.repository.CustomerTransactionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * CustomerService - Business logic layer for customer operations
 * Handles all customer-related business logic and database interactions
 * 
 * Responsibilities:
 * - Customer CRUD operations with multi-user data isolation
 * - Business logic validation
 * - Database transaction management
 * - Data isolation: Each user only sees their own customers
 */
@Slf4j
@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CustomerJdbcRepository customerJdbcRepository;
    @Autowired
    private com.example.udriBook.repository.CustomerDueRepository customerDueRepository;
    @Autowired
    private CustomerTransactionRepository transactionRepository;
    @Autowired
    private SecureOtpService secureOtpService;
    @Autowired
    private StorageService storageService;

    /**
     * Generates a human-readable, unique transactionId.
     * Format: PREFIX-YYYYMMDD-XXXXXXXX
     *   - CRD = Credit (due/bill added)
     *   - DBT = Debit  (payment received)
     * Example: CRD-20240323-A1B2C3D4
     */
    private String generateTransactionId(String prefix) {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String unique = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return prefix + "" + date + "" + unique;
    }

    public com.example.udriBook.entity.CustomerDue addCustomerDue(Long customerId, java.math.BigDecimal dueAmount,
            java.time.LocalDate dueDate,
            String description, java.util.List<org.springframework.web.multipart.MultipartFile> files,
            UserEntity user) {
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        String fileNames = storageService.uploadFiles(files);

        // Try to find existing latest due to maintain running total
        java.util.Optional<CustomerDue> existingDueOpt = customerDueRepository
                .findTopByCustomerOrderByCreatedAtDesc(customer);

        CustomerDue targetDue;
        java.math.BigDecimal previousBalance = java.math.BigDecimal.ZERO;
        
        if (existingDueOpt.isPresent()) {
            targetDue = existingDueOpt.get();
            previousBalance = targetDue.getDueAmount();
            // Update the existing latest record to keep it as the "Current Balance" record
            targetDue.setDueAmount(previousBalance.add(dueAmount));
            if (description != null && !description.trim().isEmpty()) {
                targetDue.setDescription(description);
            }
            if (dueDate != null) {
                targetDue.setDueDate(dueDate);
            }
        } else {
            // First time adding a due for this customer
            targetDue = new CustomerDue();
            targetDue.setCustomer(customer);
            targetDue.setDueAmount(dueAmount);
            targetDue.setDueDate(dueDate);
            targetDue.setDescription(description);
        }

        if (fileNames != null && !fileNames.isEmpty()) {
            String currentFiles = targetDue.getFilePath();
            if (currentFiles != null && !currentFiles.isEmpty()) {
                targetDue.setFilePath(currentFiles + "," + fileNames);
            } else {
                targetDue.setFilePath(fileNames);
            }
        }

        CustomerDue savedDue = customerDueRepository.save(targetDue);

        // Record Transaction
        CustomerTransaction transaction = new CustomerTransaction();
        transaction.setTransactionId(generateTransactionId("CRD")); // CRD = Credit
        transaction.setCustomer(customer);
        transaction.setAmount(dueAmount);
        transaction.setTransactionType(TransactionType.DUE_ADDED);
        transaction.setDescription(description != null && !description.trim().isEmpty() ? description : "New bill added");
        transaction.setBalanceAfter(savedDue.getDueAmount());
        transaction.setTransactionDate(java.time.LocalDateTime.now());
        // Attach the file names to this specific transaction
        if (fileNames != null && !fileNames.isEmpty()) {
            transaction.setFilePath(fileNames);
        }
        transactionRepository.save(transaction);

        return savedDue;
    }



    public CustomerDue saveBillUpdate(Long customerId, java.math.BigDecimal updateAmount,
            java.time.LocalDate dueDate, String description, Long id,
            java.util.List<org.springframework.web.multipart.MultipartFile> files,
            UserEntity user) {

        // 1. Find the customer first to ensure they belong to the user
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        // 2. Find the latest due record for this customer
        java.util.Optional<CustomerDue> existingDueOpt = customerDueRepository
                .findTopByCustomerOrderByCreatedAtDesc(customer);

        CustomerDue targetDue;
        if (existingDueOpt.isPresent()) {
            targetDue = existingDueOpt.get();
        } else {
            // If no records found, create a new one
            targetDue = new CustomerDue();
            targetDue.setCustomer(customer);
            targetDue.setDueAmount(java.math.BigDecimal.ZERO);
        }

        targetDue.setDueDate(dueDate);
        if (description != null && !description.trim().isEmpty()) {
            targetDue.setDescription(description);
        }

        // 4. Handle files
        String newFileNames = storageService.uploadFiles(files);
        if (newFileNames != null && !newFileNames.isEmpty()) {
            String currentFiles = targetDue.getFilePath();
            if (currentFiles != null && !currentFiles.isEmpty()) {
                targetDue.setFilePath(currentFiles + "," + newFileNames);
            } else {
                targetDue.setFilePath(newFileNames);
            }
        }

        CustomerDue savedDue;
        if (id != null) {
            // Case: Update existing transaction
            CustomerTransaction originalTx = transactionRepository.findById(id)
                    .orElseThrow(() -> new CustomException("Transaction not found"));
            
            // Calculate the difference between new amount and old amount
            java.math.BigDecimal diff = updateAmount.subtract(originalTx.getAmount());
            
            // Update the running total balance (CustomerDue)
            targetDue.setDueAmount(targetDue.getDueAmount().add(diff));
            savedDue = customerDueRepository.save(targetDue);
            
            // Update the transaction record
            originalTx.setAmount(updateAmount);
            originalTx.setDescription(description != null && !description.trim().isEmpty() ? description : originalTx.getDescription());
            
            // Preserve time if day hasn't changed, otherwise use start of day
            if (originalTx.getTransactionDate().toLocalDate().equals(dueDate)) {
                // Keep existing time
                originalTx.setTransactionDate(dueDate.atTime(originalTx.getTransactionDate().toLocalTime()));
            } else {
                originalTx.setTransactionDate(dueDate.atStartOfDay());
            }
            
            originalTx.setBalanceAfter(savedDue.getDueAmount());
            if (newFileNames != null && !newFileNames.isEmpty()) {
                String txFiles = originalTx.getFilePath();
                if (txFiles != null && !txFiles.isEmpty()) {
                    originalTx.setFilePath(txFiles + "," + newFileNames);
                } else {
                    originalTx.setFilePath(newFileNames);
                }
            }
            transactionRepository.save(originalTx);
            
            return savedDue;
        }

        // Case: New bill entry
        java.math.BigDecimal newBalance = targetDue.getDueAmount().add(updateAmount);
        targetDue.setDueAmount(newBalance);
        savedDue = customerDueRepository.save(targetDue);

        // Record New Transaction
        CustomerTransaction transaction = new CustomerTransaction();
        transaction.setCustomer(customer);
        transaction.setAmount(updateAmount);
        transaction.setTransactionType(TransactionType.DUE_ADDED); // Correct type for new bill
        transaction.setDescription(description != null && !description.trim().isEmpty() ? description : "New bill added");
        transaction.setBalanceAfter(savedDue.getDueAmount());
        transaction.setTransactionId(generateTransactionId("CRD"));
        // Attach the file names to this specific transaction
        if (newFileNames != null && !newFileNames.isEmpty()) {
            transaction.setFilePath(newFileNames);
        }
        transactionRepository.save(transaction);

        return savedDue;
    }

    public CustomerDue savePayment(Long customerId, java.math.BigDecimal updateAmount,
            String description, java.time.LocalDate paymentDate, UserEntity user) {

        // 1. Find the customer first to ensure they belong to the user
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        // 2. Find the latest due record for this customer
        CustomerDue existingDue = customerDueRepository.findTopByCustomerOrderByCreatedAtDesc(customer)
                .orElseThrow(
                        () -> new CustomException("No due records found for this customer. Please add a due first."));

        java.math.BigDecimal newDueAmount = existingDue.getDueAmount().subtract(updateAmount);
        existingDue.setDueAmount(newDueAmount);
        existingDue.setDescription(description);
        existingDue.setPaymentDate(paymentDate);

        CustomerDue savedDue = customerDueRepository.save(existingDue);

        // Record Transaction
        CustomerTransaction transaction = new CustomerTransaction();
        transaction.setTransactionId(generateTransactionId("DBT")); // DBT = Debit
        transaction.setCustomer(customer);
        transaction.setAmount(updateAmount.negate()); // Payment is a negative change
        transaction.setTransactionType(TransactionType.PAYMENT_RECEIVED);
        transaction.setDescription(
                description != null && !description.trim().isEmpty() ? description : "Payment received");
        transaction.setBalanceAfter(savedDue.getDueAmount());
        transactionRepository.save(transaction);

        return savedDue;
    }

    public CustomerDue updateTransaction(Long transactionId, java.math.BigDecimal updateAmount,
            String description, java.time.LocalDate paymentDate, UserEntity user) {

        CustomerTransaction originalTx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new CustomException("Transaction not found"));

        Customer customer = originalTx.getCustomer();
        // Validation: Transaction must belong to a customer owned by the user
        if (!customer.getUser().getId().equals(user.getId())) {
            throw new CustomException("Access denied: Transaction does not belong to your customer");
        }

        // Find the latest due record for this customer to update the current balance
        CustomerDue existingDue = customerDueRepository.findTopByCustomerOrderByCreatedAtDesc(customer)
                .orElseThrow(() -> new CustomException("No due records found for this customer."));

        java.math.BigDecimal oldPaymentAmount = originalTx.getAmount().negate();
        java.math.BigDecimal diff = updateAmount.subtract(oldPaymentAmount);

        // Update transaction record
        originalTx.setAmount(updateAmount.negate());
        originalTx.setDescription(description != null ? description : "Payment updated");
        
        // Preserve time if day hasn't changed, otherwise use start of day
        if (originalTx.getTransactionDate().toLocalDate().equals(paymentDate)) {
            originalTx.setTransactionDate(paymentDate.atTime(originalTx.getTransactionDate().toLocalTime()));
        } else {
            originalTx.setTransactionDate(paymentDate.atStartOfDay());
        }

        // Adjust the current due amount
        existingDue.setDueAmount(existingDue.getDueAmount().subtract(diff));
        CustomerDue savedDue = customerDueRepository.save(existingDue);

        originalTx.setBalanceAfter(savedDue.getDueAmount());
        transactionRepository.save(originalTx);

        return savedDue;
    }

    public List<CustomerTransaction> getTransactionHistory(Long customerId, UserEntity user) {
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        return transactionRepository.findByCustomerOrderByTransactionDateDesc(customer);
    }

    /**
     * Get activity/transactions for a user with optional filtering.
     *
     * @param username  - Username to fetch activity for
     * @param filter    - "today", "week", "month" — determines date range
     * @param startDate - Custom start date (overrides filter when filter=week)
     * @param endDate   - Custom end date (overrides default when filter=week)
     * @param limit     - Max number of records (null = no limit)
     */
    public List<CustomerTransaction> getCustomerActivity(
            String username,
            String filter,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            Integer limit) {

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime fromDt = null;
        java.time.LocalDateTime toDt = null;

        // Custom dates take precedence
        if (startDate != null || endDate != null) {
            java.time.LocalDate start = (startDate != null) ? startDate : java.time.LocalDate.of(2000, 1, 1); // fallback far past
            java.time.LocalDate end = (endDate != null) ? endDate : today; // fallback today
            fromDt = start.atStartOfDay();
            toDt = end.atTime(23, 59, 59);
        } else if (filter != null) {
            // Only apply predefined filters if custom dates are NOT provided
            switch (filter.toLowerCase()) {
                case "today":
                    fromDt = today.atStartOfDay();
                    toDt = today.atTime(23, 59, 59);
                    break;
                case "week":
                    fromDt = today.minusDays(7).atStartOfDay();
                    toDt = today.atTime(23, 59, 59);
                    break;
                case "month":
                    fromDt = today.minusDays(30).atStartOfDay();
                    toDt = today.atTime(23, 59, 59);
                    break;
                default:
                    break;
            }
        }

        List<CustomerTransaction> results;

        if (limit != null && limit > 0) {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, limit);
            if (fromDt != null) {
                results = transactionRepository.findTopByUsernameAndDateRange(username, fromDt, toDt, pageable);
            } else {
                results = transactionRepository.findTopByUsername(username, pageable);
            }
        } else {
            if (fromDt != null) {
                results = transactionRepository.findByUsernameAndDateRange(username, fromDt, toDt);
            } else {
                results = transactionRepository.findByUsername(username);
            }
        }

        return results;
    }

    /**
     * Add a new customer to the database for the authenticated user
     * 
     * @param customerDto - Customer data from API request
     * @param user        - Currently authenticated user
     * @return - Saved Customer entity
     * @throws CustomException if mobile number already exists for this user
     */
    public Customer addCustomer(CustomerDto customerDto, UserEntity user) {
        // Check if customer with same mobile already exists for this user
        Optional<Customer> existingCustomer = customerRepository.findByMobileNumberAndUser(
                customerDto.getMobileNumber().trim(), user);

        if (existingCustomer.isPresent()) {
            throw new CustomException("Customer with this mobile number already exists");
        }

        Customer customer = new Customer();
        customer.setUser(user);
        customer = mapDtoToEntity(customer, customerDto);

        // If this mobile was pre-verified via OTP before adding the customer,
        // mark it as verified and consume the one-time Redis flag.
        boolean wasVerified = secureOtpService.consumePhoneVerified(customerDto.getMobileNumber().trim());
        customer.setMobileVerified(wasVerified);
        if (wasVerified) {
            log.info("Customer {} added with mobile pre-verified via OTP", customerDto.getCustomerName());
        }

        log.info("Adding new customer: {} for user: {}", customerDto.getCustomerName(), user.getId());
        return customerRepository.save(customer);
    }

    /**
     * Get all customers for the authenticated user, ordered by latest added first
     * 
     * @param user - Currently authenticated user
     * @param page - Page number (0-based)
     * @param size - Number of records per page
     * @return - Page of customers belonging to this user
     */
    public org.springframework.data.domain.Page<Customer> getAllCustomers(UserEntity user, int page, int size) {
        log.info("Fetching all customers for user: {}, page: {}, size: {}", user.getId(), page, size);
        return customerRepository.findByUserOrderByIdDesc(user, org.springframework.data.domain.PageRequest.of(page, size));
    }

    /**
     * Get latest due for a customer
     * 
     * @param customer - Target customer
     * @return - Optional containing latest CustomerDue
     */
    public Optional<com.example.udriBook.entity.CustomerDue> getLatestDueForCustomer(Customer customer) {
        return customerDueRepository.findTopByCustomerOrderByCreatedAtDesc(customer);
    }

    /**
     * Get all bill records (dues) for a customer with multi-user isolation.
     * 
     * @param customerId 
     * @param user      
     * @return
     */
    public List<CustomerDue> getBillRecords(Long customerId, UserEntity user) {
        log.info("Fetching bill records for customerId: {} for user: {}", customerId, user.getId());
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        return customerDueRepository.findByCustomer(customer);
    }

    /**
     * Get all attachments for a customer.
     * 
     * @param customerId 
     * @param user       
     * @return 
     */
    public List<CustomerAttachmentDto> getCustomerAttachments(Long customerId, UserEntity user) {
        List<CustomerDue> billRecords = getBillRecords(customerId, user);
        
        return billRecords.stream()
                .filter(due -> due.getFilePath() != null && !due.getFilePath().isEmpty())
                .flatMap(due -> {
                    String[] fileArray = due.getFilePath().split(",");
                    return java.util.Arrays.stream(fileArray)
                            .map(fileName -> new CustomerAttachmentDto(
                                    due.getId(),
                                    fileName.trim(),
                                    due.getDescription(),
                                    due.getCreatedAt()));
                })
                .collect(Collectors.toList());
    }

    /**
     * Get a customer by ID - only if it belongs to the authenticated user
     * 
     * @param id   - Customer ID
     * @param user - Currently authenticated user
     * @return - Optional containing customer if found and belongs to user
     */
    public Optional<Customer> getCustomerById(Long id, UserEntity user) {
        log.info("Fetching customer with id: {} for user: {}", id, user.getId());
        return customerRepository.findByIdAndUser(id, user);
    }

    /**
     * Check if a customer exists for the authenticated user
     * 
     * @param id   - Customer ID
     * @param user - Currently authenticated user
     * @return - true if customer exists and belongs to user
     */
    public boolean customerExists(Long id, UserEntity user) {
        return customerRepository.existsByIdAndUser(id, user);
    }

    /**
     * Update an existing customer (only if it belongs to the authenticated user)
     * 
     * @param id          - Customer ID to update
     * @param customerDto - Updated customer data
     * @param user        - Currently authenticated user
     * @return - Updated Customer entity
     * @throws CustomException if customer not found or doesn't belong to user
     */
    public Customer updateCustomer(Long id, CustomerDto customerDto, UserEntity user) {
        log.info("Updating customer with id: {} for user: {}", id, user.getId());

        Optional<Customer> existingCustomer = customerRepository.findByIdAndUser(id, user);

        if (!existingCustomer.isPresent()) {
            log.warn("Customer not found with id: {} for user: {}", id, user.getId());
            throw new CustomException("Customer not found or access denied");
        }

        Customer customer = existingCustomer.get();
        mapDtoToEntity(customer, customerDto);

        return customerRepository.save(customer);
    }

    /**
     * Delete a customer (only if it belongs to the authenticated user)
     * 
     * @param id   - Customer ID
     * @param user - Currently authenticated user
     * @throws CustomException if customer not found or doesn't belong to user
     */
    public void deleteCustomer(Long id, UserEntity user) {
        log.info("Deleting customer with id: {} for user: {}", id, user.getId());

        if (!customerRepository.existsByIdAndUser(id, user)) {
            throw new CustomException("Customer not found or access denied");
        }

        customerRepository.deleteById(id);
    }

    /**
     * Find customer by mobile number (only for the authenticated user)
     * 
     * @param mobileNumber - Customer mobile number
     * @param user         - Currently authenticated user
     * @return - Optional containing customer if found
     */
    public Optional<Customer> getCustomerByMobileNumber(String mobileNumber, UserEntity user) {
        log.info("Fetching customer with mobile number: {} for user: {}", mobileNumber, user.getId());
        return customerRepository.findByMobileNumberAndUser(mobileNumber.trim(), user);
    }

    /**
     * Find customers by name (case-insensitive, partial match) for the
     * authenticated user
     * 
     * @param customerName - Customer name or partial name
     * @param user         - Currently authenticated user
     * @return - List of customers matching the name
     */
    public List<Customer> getCustomersByName(String customerName, UserEntity user) {
        log.info("Fetching customers with name containing: {} for user: {}", customerName, user.getId());
        return customerRepository.findByCustomerNameContainingIgnoreCaseAndUser(customerName.trim(), user);
    }

    /**
     * Helper method to map CustomerDto to Customer entity
     * Only updates fields that are not null/empty in DTO
     * 
     * @param customer    - Target Customer entity
     * @param customerDto - Source CustomerDto
     * @return - Updated Customer entity
     */
    private Customer mapDtoToEntity(Customer customer, CustomerDto customerDto) {
        if (customerDto.getCustomerName() != null && !customerDto.getCustomerName().trim().isEmpty()) {
            customer.setCustomerName(customerDto.getCustomerName().trim());
        }
        if (customerDto.getMobileNumber() != null && !customerDto.getMobileNumber().trim().isEmpty()) {
            customer.setMobileNumber(customerDto.getMobileNumber().trim());
        }
        if (customerDto.getVillage() != null && !customerDto.getVillage().trim().isEmpty()) {
            customer.setVillage(customerDto.getVillage().trim());
        }
        if (customerDto.getPincode() != null && !customerDto.getPincode().trim().isEmpty()) {
            customer.setPincode(customerDto.getPincode().trim());
        }

        if (customerDto.getCustomerCreatedDate() != null) {
            customer.setCustomerCreatedDate(customerDto.getCustomerCreatedDate());
        }
        return customer;
    }

    /**
     * Mark a customer's mobile number as verified after successful OTP confirmation.
     *
     * This is called from CustomerController AFTER SecureOtpService.verifyOtp() succeeds.
     * Ownership is validated — a user can only verify their own customers.
     *
     * @param customerId ID of the customer whose phone was verified
     * @param user       Currently authenticated user (shopkeeper)
     * @return Updated Customer entity with mobileVerified = true
     * @throws CustomException if customer not found or doesn't belong to this user
     */
    public Customer verifyCustomerMobile(Long customerId, UserEntity user) {
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(() -> new CustomException("Customer not found or access denied"));

        if (customer.isMobileVerified()) {
            log.info("Customer {} mobile already verified", customerId);
            return customer; // idempotent — already verified, return as-is
        }

        customer.setMobileVerified(true);
        Customer saved = customerRepository.save(customer);
        log.info("Customer {} mobile number verified successfully for user {}", customerId, user.getId());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Delete Bill (Due Added) Record
    // -------------------------------------------------------------------------

    /**
     * Deletes a specific DUE_ADDED / BILL_UPDATED transaction and keeps the
     * customer's ledger perfectly balanced by:
     *  1. Verifying ownership  — the transaction must belong to this user's customer.
     *  2. Cleaning up files    — any attached PDFs/images are removed from uploads/.
     *  3. Adjusting master balance — subtracts the bill amount from CustomerDue
     *                               (allows negative for advance-payment scenarios).
     *  4. Rebuilding timeline  — decrements balance_after on every later transaction.
     *
     * Wrapped in @Transactional so the full operation rolls back atomically if
     * anything fails mid-way (e.g. balance update after transaction delete).
     *
     * @param recordId — CustomerTransaction.id to delete
     * @param user     — Currently authenticated user (shopkeeper)
     * @throws CustomerNotFoundException if recordId doesn't exist or isn't owned by user (→ HTTP 404)
     * @throws CustomException           if the transaction type is not a bill type  (→ HTTP 400)
     */
    @Transactional
    public void deleteDueTransaction(Long recordId, UserEntity user) {

        // ── Step 1: Fetch transaction — 404 if it doesn't exist ───────────────
        CustomerTransaction tx = transactionRepository.findById(recordId)
                .orElseThrow(CustomerNotFoundException::new);

        // ── Step 2: Ownership check — same 404 to avoid information leakage ──
        Customer customer = tx.getCustomer();
        if (!customer.getUser().getId().equals(user.getId())) {
            throw new CustomerNotFoundException();
        }

        // ── Step 3: Only bill-type transactions are deletable here ────────────
        CustomerTransaction.TransactionType type = tx.getTransactionType();
        if (type != CustomerTransaction.TransactionType.DUE_ADDED
                && type != CustomerTransaction.TransactionType.BILL_UPDATED) {
            throw new CustomException(
                    "Only 'Due Added' or 'Bill Updated' transactions can be deleted via this endpoint.");
        }

        // ── Step 4: Capture values before deletion ────────────────────────────
        BigDecimal billAmount = tx.getAmount();          // always positive for bill types
        LocalDateTime deletedAt = tx.getTransactionDate();

        log.info("Deleting bill transaction id={} amount={} for customer={} user={}",
                recordId, billAmount, customer.getId(), user.getId());

        // ── Step 5: Best-effort file cleanup (orphan prevention) ──────────────
        storageService.deleteFiles(tx.getFilePath());

        // ── Step 6: Delete the transaction record ─────────────────────────────
        transactionRepository.deleteById(recordId);

        // ── Step 7: Adjust master CustomerDue balance ─────────────────────────
        //    Negative results are intentional — they represent an Advance Payment.
        CustomerDue latestDue = customerDueRepository
                .findTopByCustomerOrderByCreatedAtDesc(customer)
                .orElseThrow(() -> new CustomException(
                        "No balance record found for this customer."));

        latestDue.setDueAmount(latestDue.getDueAmount().subtract(billAmount));
        customerDueRepository.save(latestDue);

        // ── Step 8: Rebuild balance_after for all subsequent transactions ─────
        List<CustomerTransaction> subsequent = transactionRepository
                .findByCustomerAndTransactionDateAfterOrderByTransactionDateAsc(customer, deletedAt);

        if (!subsequent.isEmpty()) {
            for (CustomerTransaction t : subsequent) {
                // Each later entry's running balance must drop by the deleted bill amount
                t.setBalanceAfter(t.getBalanceAfter().subtract(billAmount));
            }
            transactionRepository.saveAll(subsequent);
        }

        log.info("Bill transaction {} deleted. New master balance={} for customer={}",
                recordId, latestDue.getDueAmount(), customer.getId());
    }

    // =========================================================================
    // Bulk Customer File Deletion
    // =========================================================================

    /**
     * Deletes ALL physical files associated with a customer and clears their
     * database references in both {@code CustomerDue.filePath} and
     * {@code CustomerTransaction.filePath}.
     *
     * <p><b>What this does (in order):</b>
     * <ol>
     *   <li>Ownership check — 404 if customerId doesn't belong to the caller.</li>
     *   <li>Iterates every {@link CustomerDue} for the customer, deletes the
     *       physical files from {@code uploads/}, and nulls {@code file_path}.</li>
     *   <li>Iterates every {@link CustomerTransaction} for the customer, does
     *       the same disk + DB cleanup.</li>
     *   <li>Persists all DB changes in a single batch per table.</li>
     * </ol>
     *
     * <p><b>Safety guarantees:</b>
     * <ul>
     *   <li>Fully {@code @Transactional} — any DB failure rolls back all
     *       {@code file_path} clearances atomically.</li>
     *   <li>Physical file I/O failures are non-fatal (warned, not thrown) so
     *       a missing file never aborts the overall cleanup.</li>
     *   <li>Path-traversal prevention — each resolved path is validated to
     *       stay inside {@code fileStorageLocation} before deletion.</li>
     *   <li>Idempotent — customers with zero files return cleanly with 0 counts.</li>
     * </ul>
     *
     * @param customerId ID of the customer whose files are to be purged
     * @param user       Currently authenticated user (ownership enforced)
     * @return           {@link CustomerFilesDeletionResultDto} with deletion stats
     * @throws com.example.udriBook.exception.CustomerNotFoundException
     *         if the customer does not exist or is not owned by {@code user} (→ HTTP 404)
     */
    @Transactional
    public CustomerFilesDeletionResultDto deleteAllCustomerFiles(Long customerId, UserEntity user) {

        log.info("deleteAllCustomerFiles START — customerId={} userId={}", customerId, user.getId());

        // ── Step 1: Ownership check (same 404 for missing OR foreign customer) ──
        Customer customer = customerRepository.findByIdAndUser(customerId, user)
                .orElseThrow(CustomerNotFoundException::new);

        int deletedFromDisk = 0;
        int missingOnDisk   = 0;
        int dueRecordsCleared = 0;
        int txRecordsCleared  = 0;

        // ── Step 2: CustomerDue file cleanup ──────────────────────────────────
        List<CustomerDue> dues = customerDueRepository.findByCustomer(customer);
        for (CustomerDue due : dues) {
            String fp = due.getFilePath();
            if (fp != null && !fp.trim().isEmpty()) {
                int[] counts = storageService.deleteFiles(fp);
                deletedFromDisk += counts[0];
                missingOnDisk   += counts[1];
                due.setFilePath(null);
                dueRecordsCleared++;
            }
        }
        // Batch-save only if something changed (avoids unnecessary DB round-trip)
        if (dueRecordsCleared > 0) {
            customerDueRepository.saveAll(dues);
            log.debug("Cleared file_path on {} CustomerDue record(s) for customerId={}",
                    dueRecordsCleared, customerId);
        }

        // ── Step 3: CustomerTransaction file cleanup ──────────────────────────
        List<CustomerTransaction> transactions =
                transactionRepository.findByCustomerOrderByTransactionDateDesc(customer);
        for (CustomerTransaction tx : transactions) {
            String fp = tx.getFilePath();
            if (fp != null && !fp.trim().isEmpty()) {
                int[] counts = storageService.deleteFiles(fp);
                deletedFromDisk += counts[0];
                missingOnDisk   += counts[1];
                tx.setFilePath(null);
                txRecordsCleared++;
            }
        }
        if (txRecordsCleared > 0) {
            transactionRepository.saveAll(transactions);
            log.debug("Cleared file_path on {} CustomerTransaction record(s) for customerId={}",
                    txRecordsCleared, customerId);
        }

        log.info("deleteAllCustomerFiles COMPLETE — customerId={} deletedFromDisk={} "
                + "missingOnDisk={} duesCleared={} txCleared={}",
                customerId, deletedFromDisk, missingOnDisk, dueRecordsCleared, txRecordsCleared);

        return new CustomerFilesDeletionResultDto(
                customerId,
                deletedFromDisk,
                missingOnDisk,
                dueRecordsCleared,
                txRecordsCleared);
    }

    // =========================================================================
    // Private file-I/O helpers
    // =========================================================================



    /**
     * @param recordId — CustomerTransaction.id
     * @param fileName — Exact file name to remove (e.g. "a3f1bc.pdf")
     * @param user     — Currently authenticated user
     * @throws CustomerNotFoundException if transaction not found / not owned  (→ 404)
     * @throws CustomException           if fileName is not in the attachment list (→ 400)
     */
    @Transactional
    public void deleteTransactionAttachment(Long recordId, String fileName, UserEntity user) {

        // ── Step 1: Fetch transaction and verify ownership ────────────────────
        CustomerTransaction tx = transactionRepository.findById(recordId)
                .orElseThrow(CustomerNotFoundException::new);

        if (!tx.getCustomer().getUser().getId().equals(user.getId())) {
            throw new CustomerNotFoundException();
        }

        // ── Step 2: Confirm the file is actually in this transaction ──────────
        String currentFilePath = tx.getFilePath();
        if (currentFilePath == null || currentFilePath.trim().isEmpty()) {
            throw new CustomException("This transaction has no file attachments.");
        }

        // Parse the comma-separated list and look for an exact match
        String trimmedTarget = fileName.trim();
        List<String> fileList = new java.util.ArrayList<>(
                java.util.Arrays.asList(currentFilePath.split(",")));
        fileList.replaceAll(String::trim); // normalise whitespace around each name

        boolean found = fileList.remove(trimmedTarget);
        if (!found) {
            throw new CustomException(
                    "File '" + trimmedTarget + "' not found in this transaction's attachments.");
        }

        log.info("Removing attachment '{}' from transaction id={} (user={})",
                trimmedTarget, recordId, user.getId());

        // ── Step 3: Best-effort physical file deletion ────────────────────────
        storageService.deleteFile(trimmedTarget);

        // ── Step 4: Rebuild file_path without the removed file ────────────────
        String updatedFilePath = fileList.isEmpty() ? null : String.join(",", fileList);
        tx.setFilePath(updatedFilePath);

        // ── Step 5: Persist ───────────────────────────────────────────────────
        transactionRepository.save(tx);

        log.info("Attachment '{}' removed. Updated file_path='{}' for transaction id={}",
                trimmedTarget, updatedFilePath, recordId);
    }
}
