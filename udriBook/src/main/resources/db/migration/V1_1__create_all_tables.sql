-- V1.1 Create all base tables if they don't exist

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    store_name VARCHAR(100) NOT NULL,
    store_type VARCHAR(50),
    phone_number VARCHAR(15) UNIQUE,
    email_id VARCHAR(100) UNIQUE,
    village_name VARCHAR(100),
    pincode VARCHAR(10),
    password_hash VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mobile (phone_number),
    INDEX idx_email (email_id),
    INDEX idx_owner_name (owner_name),
    INDEX idx_store_name (store_name),
    INDEX idx_user_id (id)
);

CREATE TABLE IF NOT EXISTS user_profile_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    image_urly VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    login_time DATETIME NOT NULL,
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedbacks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comments TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS u_customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(10) NOT NULL,
    village_name VARCHAR(255) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    email_id VARCHAR(100) UNIQUE DEFAULT NULL,
    customer_created_date DATE NOT NULL,
    mobile_verified TINYINT(1) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_mobile (user_id, mobile_number),
    INDEX idx_user_id (user_id),
    INDEX idx_mobile (mobile_number),
    INDEX idx_created_date (customer_created_date)
);

CREATE TABLE IF NOT EXISTS customer_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    transaction_date DATETIME NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    transaction_id VARCHAR(30) UNIQUE NOT NULL,
    file_path TEXT DEFAULT NULL,
    CONSTRAINT fk_transaction_customer FOREIGN KEY (customer_id) REFERENCES u_customers(id) ON DELETE CASCADE,
    INDEX idx_trans_customer (customer_id),
    INDEX idx_trans_date (transaction_date)
);

CREATE TABLE IF NOT EXISTS customer_dues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    due_amount DECIMAL(10, 2) NOT NULL,
    due_date DATE DEFAULT NULL,
    file_path VARCHAR(255),
    description TEXT,
    payment_date DATE DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_due_customer FOREIGN KEY (customer_id) REFERENCES u_customers(id) ON DELETE CASCADE,
    INDEX idx_customer_id (customer_id),
    INDEX idx_due_amount (due_amount),
    INDEX idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
