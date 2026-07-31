package com.example.udriBook.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
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

import com.example.udriBook.dto.CustomerDetailResponseDto;
import com.example.udriBook.dto.CustomerDto;
import com.example.udriBook.dto.CustomerResponseDto;
import com.example.udriBook.dto.CustomerTransactionResponseDto;
import com.example.udriBook.entity.Customer;
import com.example.udriBook.entity.CustomerDue;
import com.example.udriBook.entity.CustomerTransaction;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.exception.CustomException;
import com.example.udriBook.repository.CustomerJdbcRepository;
import com.example.udriBook.service.CustomerService;
import com.example.udriBook.util.ApiResponse;
import com.example.udriBook.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*"
// methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
// RequestMethod.DELETE, RequestMethod.OPTIONS }
)
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private CustomerJdbcRepository customerJdbcRepository;
    @Autowired
    private SecurityUtil securityUtil;
    @Autowired
    private com.example.udriBook.service.SecureOtpService secureOtpService;

    private static final String CUSTOMER_NOT_FOUND = "Customer not found with id: ";
    private static final String INVALID_ID = "Invalid customer ID. ID must be greater than 0";

    /**
     * POST API - Create a new customer
     * Endpoint: POST /api/customers/add
     * 
     * @param customerDto   - Customer details with validations
     * @param bindingResult - Validation results
     * @param userIdentity  - User's phone or email from header
     * @return - ApiResponse with created customer or error message
     */
    @PostMapping("/add")
    public ResponseEntity<?> addCustomer(@Valid @RequestBody CustomerDto customerDto,
            BindingResult bindingResult) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            // Validate input data
            if (bindingResult.hasErrors()) {
                List<String> errors = getValidationErrors(bindingResult);
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), errors);
            }

            Customer savedCustomer = customerService.addCustomer(customerDto, currentUser);

            return buildSuccessResponse(HttpStatus.CREATED.value(),
                    "Customer created successfully",
                    mapToDetailDto(savedCustomer));
        } catch (CustomException e) {
            log.error("Validation error adding customer: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        } catch (Exception e) {
            log.error("Error adding customer", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error adding customer: " + e.getMessage());
        }
    }

    /**
     * GET API - Retrieve all customers for current user
     * Endpoint: GET /api/customers/all
     * 
     * @param userIdentity - User's phone or email from header
     * @return - ApiResponse with list of all customers of current user
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            log.info("Fetching all customers for user: {}", currentUser.getId());
            org.springframework.data.domain.Page<Customer> customersPage = customerService.getAllCustomers(currentUser, page, size);

            if (customersPage.isEmpty()) {
                log.info("No customers found for user: {}", currentUser.getId());
                return buildErrorResponse(HttpStatus.NOT_FOUND.value(), "No customers found");
            }

            org.springframework.data.domain.Page<CustomerDetailResponseDto> customerDetailList = customersPage
                    .map(this::mapToDetailDto);

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "Customers retrieved successfully",
                    customerDetailList);
        } catch (CustomException e) {
            log.error("User error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving customers", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error retrieving customers: " + e.getMessage());
        }
    }

    /**
     * GET API - Retrieve all customers for UI display (optimized response)
     * Endpoint: GET /api/customers/list
     * 
     * @param userIdentity - User's phone or email from header
     * @return - ApiResponse with list of customers (name, phone, balance, due date)
     */
    @GetMapping("/list")
    public ResponseEntity<?> getCustomersList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            org.springframework.data.domain.Page<Customer> customersPage = customerService.getAllCustomers(currentUser, page, size);

            if (customersPage.isEmpty()) {
                return buildErrorResponse(HttpStatus.NOT_FOUND.value(),
                        "No customers found");
            }

            // Convert to response DTO with only required fields
            org.springframework.data.domain.Page<CustomerResponseDto> customerList = customersPage
                    .map(customer -> {
                        CustomerDue latestDue = customerService
                                .getLatestDueForCustomer(customer)
                                .orElse(null);
                        return new CustomerResponseDto(
                                customer.getId(),
                                customer.getCustomerName(),
                                customer.getMobileNumber(),
                                latestDue != null ? latestDue.getDueAmount() : null,
                                latestDue != null ? latestDue.getDueDate() : null);
                    });
            long totalCustomers = customerJdbcRepository.getNumberOfCustomers(currentUser.getId());
            ApiResponse<org.springframework.data.domain.Page<CustomerResponseDto>> response = ApiResponse.<org.springframework.data.domain.Page<CustomerResponseDto>>success(
                    HttpStatus.OK.value(),
                    "Customers retrieved successfully",
                    totalCustomers,
                    customerList);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("User error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving customers list", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error retrieving customers: " + e.getMessage());
        }
    }

    /**
     * GET API - Retrieve customer by ID (only if belongs to current user)
     * Endpoint: GET /api/customers/{id}
     * 
     * @param id           - Customer ID
     * @param userIdentity - User's phone or email from header
     * @return - ApiResponse with customer details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable Long id) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            // Validate ID
            if (id <= 0) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_ID);
            }

            Optional<Customer> customer = customerService.getCustomerById(id, currentUser);

            if (customer.isPresent()) {
                return buildSuccessResponse(HttpStatus.OK.value(),
                        "Customer retrieved successfully",
                        mapToDetailDto(customer.get()));
            } else {
                log.warn("Customer not found with id: {} for user: {}", id, currentUser.getId());
                return buildErrorResponse(HttpStatus.NOT_FOUND.value(),
                        CUSTOMER_NOT_FOUND + id);
            }
        } catch (CustomException e) {
            log.error("User error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            log.error("Error retrieving customer by id: {}", id, e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error retrieving customer: " + e.getMessage());
        }
    }

    /**
     * Endpoint: PUT /api/customers/update/{id}
     * 
     * @param id            - Customer ID to update
     * @param customerDto   - Updated customer details with validations
     * @param bindingResult - Validation results
     * @param userIdentity  - User's phone or email from header
     * @return - ApiResponse with updated customer or error message
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id,
            @Valid @RequestBody CustomerDto customerDto,
            BindingResult bindingResult) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            // Validate ID
            if (id <= 0) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_ID);
            }

            // Validate input data
            if (bindingResult.hasErrors()) {
                List<String> errors = getValidationErrors(bindingResult);
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), errors);
            }

            log.info("Updating customer with id: {} for user: {}", id, currentUser.getId());

            // Update customer (will throw CustomException if not found or access denied)
            Customer updatedCustomer = customerService.updateCustomer(id, customerDto, currentUser);

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "Customer updated successfully",
                    mapToDetailDto(updatedCustomer));
        } catch (CustomException e) {
            return buildErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error updating customer: " + e.getMessage());
        }
    }

    /**
     * DELETE API - Delete customer by ID (only if belongs to current user)
     * Endpoint: DELETE /api/customers/delete/{id}
     * 
     * @param id           - Customer ID to delete
     * @param userIdentity - User's phone or email from header
     * @return - ApiResponse with success or error message
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            // Validate ID
            if (id <= 0) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_ID);
            }

            log.info("Deleting customer with id: {} for user: {}", id, currentUser.getId());

            // Delete customer (will throw CustomException if not found or access denied)
            customerService.deleteCustomer(id, currentUser);

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "Customer deleted successfully",
                    null);
        } catch (CustomException e) {
            return buildErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error deleting customer: " + e.getMessage());
        }
    }

    /**
     * GET API - Search customers by name or mobile number
     * Endpoint: GET /api/customers/search?name={name}&mobile={mobile}
     * 
     * @param name         - Customer name or partial name (optional)
     * @param mobile       - Customer mobile number (optional)
     * @param userIdentity - User's phone or email from header
     * @return - ApiResponse with list of customers or error message
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchCustomers(@RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "mobile", required = false) String mobile) {
        try {
            // Get current user for multi-user isolation
            UserEntity currentUser = securityUtil.getCurrentUser();

            // Validate that at least one search parameter is provided
            if ((name == null || name.trim().isEmpty()) && (mobile == null || mobile.trim().isEmpty())) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(),
                        "At least one search parameter (name or mobile) must be provided");
            }

            // Validate that both parameters are not provided at the same time
            if (name != null && !name.trim().isEmpty() && mobile != null && !mobile.trim().isEmpty()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(),
                        "Please provide only one search parameter at a time (name or mobile)");
            }

            List<Customer> customers;

            if (mobile != null && !mobile.trim().isEmpty()) {
                // Search by mobile number
                Optional<Customer> customer = customerService.getCustomerByMobileNumber(mobile, currentUser);
                customers = customer.map(List::of).orElse(List.of());
                if (customers.isEmpty()) {
                    return buildErrorResponse(HttpStatus.NOT_FOUND.value(),
                            "Customer not found with mobile number: " + mobile);
                }
            } else {
                // Search by name
                customers = customerService.getCustomersByName(name, currentUser);
                if (customers.isEmpty()) {
                    return buildErrorResponse(HttpStatus.NOT_FOUND.value(),
                            "No customers found with name containing: " + name);
                }
            }

            List<CustomerDetailResponseDto> customerDetailList = customers.stream()
                    .map(this::mapToDetailDto)
                    .collect(Collectors.toList());

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "Customers retrieved successfully",
                    customerDetailList);
        } catch (CustomException e) {
            log.error("User error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error retrieving customers: " + e.getMessage());
        }
    }

    /**
     * GET API - Retrieve activity/transactions for a user
     * Endpoint: GET /api/customers/activity/{username}
     *
     * @param username  - Username whose activity to retrieve
     * @param filter    - "today", "week", or "month" (optional)
     * @param limit     - Max number of records to return (optional, e.g. 5)
     * @param startDate - Custom start date yyyy-MM-dd (used with filter=week)
     * @param endDate   - Custom end date yyyy-MM-dd (used with filter=week)
     */
    @GetMapping("/activity/{username}")
    public ResponseEntity<?> getCustomerActivity(
            @PathVariable String username,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Parse optional dates
            LocalDate parsedStart = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
            LocalDate parsedEnd   = (endDate   != null && !endDate.isBlank())   ? LocalDate.parse(endDate)   : null;

            List<CustomerTransaction> transactions = customerService.getCustomerActivity(
                    username, filter, parsedStart, parsedEnd, limit);

            // Map to response DTO
            List<CustomerTransactionResponseDto> result = transactions.stream()
                    .map(t -> {
                        String type = mapTransactionType(t.getTransactionType());
                        return new CustomerTransactionResponseDto(
                                t.getId(),
                                t.getTransactionId(),
                                t.getCustomer().getCustomerName(),
                                t.getAmount().abs(),          // always positive amount for display
                                type,
                                t.getDescription(),
                                t.getTransactionDate(),
                                t.getBalanceAfter(),
                                t.getFilePath());
                        })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching activity for user: {}", username, e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error fetching activity: " + e.getMessage());
        }
    }

  
    @PostMapping("/phone/send-otp")
    public ResponseEntity<?> sendOtpToPhone(@RequestParam String mobile) {
        try {
            if (mobile == null || mobile.isBlank()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Mobile number is required");
            }

            // Generate OTP — BCrypt-hashed and stored in Redis (TTL 5 min)
            String otp = secureOtpService.generateOtp(mobile.trim());

            // TODO: Send via SMS in production:
            // msg91Service.sendOtp(mobile.trim());
            log.info("OTP generated for unregistered mobile: {}", maskIdentifier(mobile));

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "OTP sent to " + maskIdentifier(mobile),
                    java.util.Map.of(
                        // ⚠ REMOVE devOtp in production — only for testing
                        "devOtp", otp
                    ));

        } catch (com.example.udriBook.exception.OtpException ex) {
            return buildErrorResponse(ex.getStatus().value(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Error sending OTP to mobile", ex);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error sending OTP: " + ex.getMessage());
        }
    }

    
    @PostMapping("/phone/verify-otp")
    public ResponseEntity<?> verifyPhoneOtp(
            @RequestParam String mobile,
            @RequestParam String otp) {
        try {
            if (mobile == null || mobile.isBlank()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Mobile number is required");
            }
            if (otp == null || otp.isBlank()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "OTP is required");
            }

            // Verify OTP — throws OtpException on wrong/expired/brute-force
            secureOtpService.verifyOtp(mobile.trim(), otp.trim());

            // Store "phone_verified" flag in Redis for 15 minutes.
            // addCustomer() will consume this flag and set mobileVerified=true.
            secureOtpService.markPhoneVerified(mobile.trim());

            log.info("Mobile {} pre-verified. Ready for customer creation (valid 15 min).",
                    maskIdentifier(mobile));

            return buildSuccessResponse(HttpStatus.OK.value(),
                    "Mobile number verified. You can now add the customer.",
                    java.util.Map.of(
                        "phoneVerified", true,
                        "note", "Call POST /api/customers/add within 15 minutes"
                    ));

        } catch (com.example.udriBook.exception.OtpException ex) {
            return buildErrorResponse(ex.getStatus().value(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Error verifying OTP for mobile", ex);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Error verifying OTP: " + ex.getMessage());
        }
    }

    /** Mask phone/email for safe logging and response hints */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 4) return "****";
        if (identifier.contains("@")) {
            int at = identifier.indexOf('@');
            return identifier.substring(0, Math.min(2, at)) + "***" + identifier.substring(at);
        }
        return identifier.substring(0, 2) + "*****" + identifier.substring(identifier.length() - 3);
    }



    /** Map internal TransactionType to simplified client-friendly string */
    private String mapTransactionType(CustomerTransaction.TransactionType type) {
        if (type == null) return "UNKNOWN";
        switch (type) {
            case PAYMENT_RECEIVED: return "PAYMENT";
            case BILL_UPDATED:     return "BILL";
            case DUE_ADDED:        return "BILL";
            default:               return type.name();
        }
    }

    /**
     * Helper method to extract validation errors from BindingResult
     * 
     * @param bindingResult - Spring validation result
     * @return - List of error messages
     */
    private List<String> getValidationErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.toList());
    }

    /**
     * Helper method to build success response using ApiResponse utility
     * 
     * @param status  - HTTP status code
     * @param message - Success message
     * @param data    - Response data
     * @return - ResponseEntity with ApiResponse
     */
    private <T> ResponseEntity<?> buildSuccessResponse(int status, String message, T data) {
        ApiResponse<T> response = ApiResponse.success(status, message, data);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }

    /**
     * Helper method to build error response using ApiResponse utility
     * 
     * @param status  - HTTP status code
     * @param message - Error message
     * @return - ResponseEntity with ApiResponse
     */
    private ResponseEntity<?> buildErrorResponse(int status, String message) {
        ApiResponse<?> response = ApiResponse.error(status, message);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }

    /**
     * Helper method to build error response with multiple error messages
     * 
     * @param status - HTTP status code
     * @param errors - List of error messages
     * @return - ResponseEntity with ApiResponse
     */
    private ResponseEntity<?> buildErrorResponse(int status, List<String> errors) {
        ApiResponse<?> response = ApiResponse.error(status, errors);
        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }

    /**
     * Helper method to map Customer entity to CustomerDetailResponseDto
     * 
     * @param customer - Customer entity
     * @return - CustomerDetailResponseDto
     */
    private CustomerDetailResponseDto mapToDetailDto(Customer customer) {
        CustomerDue latestDue = customerService.getLatestDueForCustomer(customer)
                .orElse(null);
        return new CustomerDetailResponseDto(
                customer.getId(),
                customer.getUser() != null ? customer.getUser().getId() : null,
                customer.getCustomerName(),
                customer.getMobileNumber(),
                customer.getVillage(),
                customer.getPincode(),
                customer.getEmailId(),
                customer.getCustomerCreatedDate(),
                latestDue != null ? latestDue.getDueAmount() : null,
                latestDue != null ? latestDue.getDueDate() : null,
                latestDue != null ? latestDue.getPaymentDate() : null);
    }
}
