CREATE TABLE pos_cash_movements (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    cashier VARCHAR(100) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    reason VARCHAR(250) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, session_id, idempotency_key),
    CONSTRAINT fk_cash_movement_session FOREIGN KEY (tenant_id, branch_id, cashier, session_id)
        REFERENCES pos_sessions (tenant_id, branch_id, cashier, id),
    CONSTRAINT chk_cash_movement_type CHECK (movement_type IN ('CASH_IN', 'CASH_OUT', 'SAFE_DROP')),
    CONSTRAINT chk_cash_movement_amount CHECK (amount > 0),
    CONSTRAINT chk_cash_movement_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
);
