-- V6: Fix missing columns and tables detected by entity-vs-schema audit

-- 1. Add missing `version` column to `u_customers` (used by @Version for optimistic locking)
ALTER TABLE u_customers
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- 2. Add missing `version` column to `customer_dues` (used by @Version for optimistic locking)
ALTER TABLE customer_dues
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- 3. Create missing `sales_transaction` table (mapped by SalesTransaction entity)
CREATE TABLE IF NOT EXISTS sales_transaction (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_identity         VARCHAR(100) NOT NULL,
    amount                DECIMAL(12, 2) NOT NULL,
    payment_mode          VARCHAR(10) NOT NULL,
    transaction_date_time DATETIME NOT NULL,
    notes                 VARCHAR(500),
    created_at            DATETIME,
    INDEX idx_sales_user_date (user_identity, transaction_date_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
