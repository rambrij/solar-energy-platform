CREATE TABLE vendor_payment_attempts (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 order_id VARCHAR(36) NOT NULL,
 method VARCHAR(8) NOT NULL CHECK(method IN ('UPI','CARD')),
 merchant_account VARCHAR(80) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('PENDING','CONFIRMED')),
 amount DECIMAL(19,2) NOT NULL CHECK(amount>=0),
 created_at TIMESTAMP NOT NULL,
 confirmed_at TIMESTAMP NULL,
 provider_reference VARCHAR(80) NULL,
 UNIQUE(tenant_id,order_id),
 FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id)
);
