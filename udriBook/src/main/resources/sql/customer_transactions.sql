CREATE TABLE IF NOT EXISTS customer_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    transaction_date DATETIME NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_transaction_customer FOREIGN KEY (customer_id) REFERENCES u_customers(id) ON DELETE CASCADE,
    INDEX idx_trans_customer (customer_id),
    INDEX idx_trans_date (transaction_date)
);

ALTER TABLE customer_transactions
  ADD COLUMN transaction_id VARCHAR(30) UNIQUE NOT NULL;