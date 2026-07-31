-- Create u_customers table with auto-update timestamps and user_id foreign key
CREATE TABLE IF NOT EXISTS u_customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(10) NOT NULL,
    village_name VARCHAR(255) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    email_id VARCHAR(100) UNIQUE DEFAULT NULL,
    customer_created_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_mobile (user_id, mobile_number),
    INDEX idx_user_id (user_id),
    INDEX idx_mobile (mobile_number),
    INDEX idx_created_date (customer_created_date)
);
