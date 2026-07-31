// package com.example.udriBook.job;

// import java.time.LocalDate;
// import java.util.List;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// import com.example.udriBook.entity.Customer;
// import com.example.udriBook.service.CustomerService;

// import lombok.extern.slf4j.Slf4j;

// /**
//  * DueCustomerJob - Scheduled job to fetch and display customers with dues
//  * 
//  * Job Details:
//  * - Runs daily at midnight (00:00) or can be customized
//  * - Fetches all customers whose due date is today
//  * - Logs their details for notification/processing
//  * 
//  * Cron Expression Format: (second minute hour day month day-of-week)
//  * - 0 0 * * * ? = Daily at 00:00 (midnight)
//  * - 0 9 * * * ? = Daily at 9:00 AM
//  * - 0 /1 * * * ? = Every hour
//  * - 0 0 * * 0 ? = Weekly on Sunday
//  */
// @Slf4j
// @Component
// public class DueCustomerJob {
    
//     @Autowired
//     private CustomerService customerService;
    
//     /**
//      * Scheduled method to fetch customers with dues today
//      * Runs daily at midnight
//      */
//     @Scheduled(cron = "0 0 * * * ?") // Daily at 00:00 (midnight)
//     public void processDueCustomers() {
//         log.info("========== STARTING DUE CUSTOMER JOB ==========");
//         log.info("Job triggered at: {}", LocalDate.now());
        
//         try {
            
//             if (dueCustomers.isEmpty()) {
//                 log.info("No customers with dues today");
//                 log.info("========== DUE CUSTOMER JOB COMPLETED ==========");
//                 return;
//             }
            
//             log.info("Found {} customers with dues today", dueCustomers.size());
//             log.info("========== DUE CUSTOMERS DETAILS ==========");
            
//             // Display each customer's details
//             dueCustomers.forEach(customer -> {
//                 log.info("-------------------------------------------");
//                 log.info("Customer ID: {}", customer.getId());
//                 log.info("Customer Name: {}", customer.getCustomerName());
//                 log.info("Mobile Number: {}", customer.getMobileNumber());
//                 log.info("Village: {}", customer.getVillage());
//                 log.info("Pincode: {}", customer.getPincode());
//                 log.info("Customer Created Date: {}", customer.getCustomerCreatedDate());
//                 log.info("-------------------------------------------");
//             });
            
//             log.info("========== DUE CUSTOMERS PROCESSING COMPLETE ==========");
            
//         } catch (Exception e) {
//             log.error("Error in DueCustomerJob while processing due customers", e);
//         }
        
//         log.info("========== DUE CUSTOMER JOB FINISHED ==========");
//     }
// }
