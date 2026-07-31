CREATE TABLE users (
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
