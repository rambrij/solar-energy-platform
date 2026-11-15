CREATE TABLE order_buyer_snapshots (
 tenant_id BIGINT NOT NULL,
 order_id VARCHAR(36) NOT NULL,
 name VARCHAR(160),
 contact VARCHAR(160),
 tax_id VARCHAR(80),
 address VARCHAR(1000),
 captured_at TIMESTAMP NOT NULL,
 PRIMARY KEY(tenant_id,order_id),
 FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id)
);
