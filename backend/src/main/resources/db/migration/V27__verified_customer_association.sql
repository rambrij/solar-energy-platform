CREATE TABLE tenant_customers (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
 consent_at TIMESTAMP NOT NULL, provenance VARCHAR(40) NOT NULL,
 UNIQUE(tenant_id,customer_id), UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id) REFERENCES tenants(id), FOREIGN KEY(customer_id) REFERENCES customer_profiles(id)
);
CREATE TABLE pos_customer_challenges (
 token_hash VARCHAR(64) PRIMARY KEY, tenant_id BIGINT NOT NULL, order_id VARCHAR(36) NOT NULL,
 expires_at TIMESTAMP NOT NULL, approved_customer_id BIGINT NULL,
 FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id), FOREIGN KEY(approved_customer_id) REFERENCES customer_profiles(id)
);
CREATE INDEX ix_customer_challenge_order ON pos_customer_challenges(tenant_id,order_id);
