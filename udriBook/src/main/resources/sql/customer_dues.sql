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