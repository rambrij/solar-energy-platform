CREATE TABLE inventory_receipts (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 branch_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL CHECK(quantity>0),
 actor VARCHAR(100) NOT NULL,
 reason VARCHAR(250) NOT NULL,
 command_key VARCHAR(80) NOT NULL,
 available_after INT NOT NULL CHECK(available_after>=0),
 created_at TIMESTAMP NOT NULL,
 UNIQUE(tenant_id,branch_id,command_key),
 FOREIGN KEY(tenant_id,branch_id) REFERENCES branches(tenant_id,id),
 FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id)
);
CREATE INDEX inventory_receipts_history ON inventory_receipts(tenant_id,branch_id,created_at,id);
