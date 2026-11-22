ALTER TABLE branches ADD cash_refunds_enabled BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE pos_cash_refunds (
 id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,return_id VARCHAR(36) NOT NULL,order_id VARCHAR(36) NOT NULL,
 original_receipt BIGINT NOT NULL,cashier VARCHAR(100) NOT NULL,return_requester VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL,request_hash VARCHAR(64) NOT NULL,quote_hash VARCHAR(64) NOT NULL,
 amount DECIMAL(19,2) NOT NULL CHECK(amount>0),reason VARCHAR(250) NOT NULL,paid_at TIMESTAMP NOT NULL,
 UNIQUE(tenant_id,id),UNIQUE(tenant_id,id,order_id),UNIQUE(tenant_id,return_id),UNIQUE(tenant_id,idempotency_key),
 FOREIGN KEY(tenant_id,branch_id,return_id) REFERENCES pos_returns(tenant_id,branch_id,id),
 FOREIGN KEY(tenant_id,branch_id,order_id) REFERENCES orders(tenant_id,branch_id,id),
 FOREIGN KEY(tenant_id,branch_id,cashier,session_id) REFERENCES pos_sessions(tenant_id,branch_id,cashier,id),
 CHECK(cashier<>return_requester)
);
CREATE TABLE pos_cash_refund_items (
 tenant_id BIGINT NOT NULL,refund_id VARCHAR(36) NOT NULL,return_id VARCHAR(36) NOT NULL,order_id VARCHAR(36) NOT NULL,
 product_id BIGINT NOT NULL,name VARCHAR(160) NOT NULL,quantity INT NOT NULL CHECK(quantity>0),
 net DECIMAL(19,2) NOT NULL CHECK(net>=0),tax DECIMAL(19,2) NOT NULL CHECK(tax>=0),amount DECIMAL(19,2) NOT NULL,
 PRIMARY KEY(tenant_id,refund_id,product_id),
 FOREIGN KEY(tenant_id,refund_id,order_id) REFERENCES pos_cash_refunds(tenant_id,id,order_id),
 FOREIGN KEY(tenant_id,return_id,product_id) REFERENCES pos_return_items(tenant_id,return_id,product_id),
 FOREIGN KEY(tenant_id,order_id,product_id) REFERENCES order_items(tenant_id,order_id,product_id),
 CHECK(amount=net+tax)
);
CREATE INDEX ix_refund_drawer ON pos_cash_refunds(tenant_id,session_id);
CREATE INDEX ix_refund_branch_paid ON pos_cash_refunds(tenant_id,branch_id,paid_at,id);
CREATE INDEX ix_refund_original_items ON pos_cash_refund_items(tenant_id,order_id,product_id);
