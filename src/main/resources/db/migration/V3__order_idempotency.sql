CREATE TABLE order_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation VARCHAR(30) NOT NULL,
    request_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_order_idempotency UNIQUE (user_id, operation, request_key),
    CONSTRAINT fk_order_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_idempotency_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_idempotency_user_op_key (user_id, operation, request_key)
) ENGINE=InnoDB;
