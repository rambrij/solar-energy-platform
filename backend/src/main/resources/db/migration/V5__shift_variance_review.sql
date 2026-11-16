ALTER TABLE pos_sessions ADD cash_variance DECIMAL(19,2) NULL;
ALTER TABLE pos_sessions ADD variance_reason VARCHAR(250) NULL;
ALTER TABLE pos_sessions ADD close_request_id VARCHAR(36) NULL;
ALTER TABLE pos_sessions ADD close_requested_at TIMESTAMP NULL;
ALTER TABLE pos_sessions ADD reviewed_by VARCHAR(100) NULL;
ALTER TABLE pos_sessions ADD review_reason VARCHAR(250) NULL;
ALTER TABLE pos_sessions ADD reviewed_at TIMESTAMP NULL;
-- Retain the original OPEN-only key for compatibility; this guard reserves the
-- till during both OPEN and CLOSING without changing an applied migration.
ALTER TABLE pos_sessions ADD active_register_guard BIGINT GENERATED ALWAYS AS
    (CASE WHEN state IN ('OPEN','CLOSING') THEN register_id ELSE NULL END);
CREATE UNIQUE INDEX uk_session_open_closing_register ON pos_sessions(tenant_id,active_register_guard);
ALTER TABLE pos_sessions ADD CONSTRAINT fk_session_reviewer
    FOREIGN KEY(tenant_id,branch_id,reviewed_by) REFERENCES employees(tenant_id,branch_id,username);
ALTER TABLE pos_sessions ADD CONSTRAINT chk_session_independent_review
    CHECK(reviewed_by IS NULL OR reviewed_by <> cashier);
UPDATE pos_sessions SET cash_variance=counted_cash-expected_cash,close_request_id=id,close_requested_at=closed_at
    WHERE state='CLOSED';

CREATE TABLE pos_shift_reviews (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    cashier VARCHAR(100) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    close_request_id VARCHAR(36) NOT NULL,
    reviewer VARCHAR(100) NOT NULL,
    decision VARCHAR(24) NOT NULL CHECK(decision IN ('APPROVE','RETURN_FOR_CORRECTION')),
    reason VARCHAR(250) NOT NULL CHECK(CHAR_LENGTH(TRIM(reason)) > 0),
    variance_reason VARCHAR(250) NOT NULL,
    expected_cash DECIMAL(19,2) NOT NULL,
    counted_cash DECIMAL(19,2) NOT NULL,
    cash_variance DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id,session_id,close_request_id),
    CHECK(reviewer <> cashier),
    FOREIGN KEY(tenant_id,branch_id,cashier,session_id) REFERENCES pos_sessions(tenant_id,branch_id,cashier,id),
    FOREIGN KEY(tenant_id,branch_id,reviewer) REFERENCES employees(tenant_id,branch_id,username)
);
