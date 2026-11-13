ALTER TABLE branches ADD pos_return_window_days INT NOT NULL DEFAULT 0 CHECK(pos_return_window_days>=0 AND pos_return_window_days<=365);
CREATE UNIQUE INDEX uk_order_return_branch ON orders(tenant_id,branch_id,id);
CREATE TABLE pos_returns (
 id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,order_id VARCHAR(36) NOT NULL,
 requester VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,idempotency_key VARCHAR(80) NOT NULL,request_hash VARCHAR(64) NOT NULL,
 reason VARCHAR(250) NOT NULL,proof VARCHAR(32) NOT NULL CHECK(proof IN ('ORIGINAL_RECEIPT','ORDER_AND_ITEM_VERIFIED')),proof_note VARCHAR(250) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('REQUESTED','APPROVED','REJECTED','INSPECTED')),created_at TIMESTAMP NOT NULL,
 reviewed_by VARCHAR(100),review_reason VARCHAR(250),reviewed_at TIMESTAMP NULL,
 inspected_by VARCHAR(100),inspection_hash VARCHAR(64),inspected_at TIMESTAMP NULL,
 UNIQUE(tenant_id,id),UNIQUE(tenant_id,branch_id,id),UNIQUE(tenant_id,id,order_id),UNIQUE(tenant_id,idempotency_key),
 FOREIGN KEY(tenant_id,branch_id,order_id) REFERENCES orders(tenant_id,branch_id,id),
 FOREIGN KEY(tenant_id,branch_id,requester,session_id) REFERENCES pos_sessions(tenant_id,branch_id,cashier,id),
 CHECK(reviewed_by IS NULL OR reviewed_by<>requester),CHECK(inspected_by IS NULL OR inspected_by<>requester),
 CHECK((state='REQUESTED' AND reviewed_by IS NULL AND inspected_by IS NULL) OR (state IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND review_reason IS NOT NULL AND reviewed_at IS NOT NULL AND inspected_by IS NULL) OR (state='INSPECTED' AND reviewed_by IS NOT NULL AND inspected_by IS NOT NULL AND inspection_hash IS NOT NULL AND inspected_at IS NOT NULL))
);
CREATE TABLE pos_return_items (
 tenant_id BIGINT NOT NULL,return_id VARCHAR(36) NOT NULL,order_id VARCHAR(36) NOT NULL,product_id BIGINT NOT NULL,
 name VARCHAR(160) NOT NULL,quantity INT NOT NULL CHECK(quantity>0),
 received_condition VARCHAR(16),disposition VARCHAR(16),inspection_note VARCHAR(250),
 PRIMARY KEY(tenant_id,return_id,product_id),
 FOREIGN KEY(tenant_id,return_id,order_id) REFERENCES pos_returns(tenant_id,id,order_id),
 FOREIGN KEY(tenant_id,order_id,product_id) REFERENCES order_items(tenant_id,order_id,product_id),
 CHECK((received_condition IS NULL AND disposition IS NULL) OR (received_condition IN ('SELLABLE','DAMAGED','INCOMPLETE') AND disposition IN ('RESTOCK','QUARANTINE') AND inspection_note IS NOT NULL AND (disposition<>'RESTOCK' OR received_condition='SELLABLE')))
);
CREATE TABLE pos_return_events (
 id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,return_id VARCHAR(36) NOT NULL,actor VARCHAR(100) NOT NULL,
 action VARCHAR(16) NOT NULL,note VARCHAR(250) NOT NULL,created_at TIMESTAMP NOT NULL,
 FOREIGN KEY(tenant_id,return_id) REFERENCES pos_returns(tenant_id,id)
);
CREATE TABLE inventory_return_dispositions (
 tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,return_id VARCHAR(36) NOT NULL,product_id BIGINT NOT NULL,
 quantity INT NOT NULL CHECK(quantity>0),disposition VARCHAR(16) NOT NULL CHECK(disposition IN ('RESTOCK','QUARANTINE')),
 received_condition VARCHAR(16) NOT NULL CHECK(received_condition IN ('SELLABLE','DAMAGED','INCOMPLETE')),
 actor VARCHAR(100) NOT NULL,created_at TIMESTAMP NOT NULL,
 PRIMARY KEY(tenant_id,return_id,product_id),
 FOREIGN KEY(tenant_id,branch_id,return_id) REFERENCES pos_returns(tenant_id,branch_id,id),
 FOREIGN KEY(tenant_id,return_id,product_id) REFERENCES pos_return_items(tenant_id,return_id,product_id),
 FOREIGN KEY(tenant_id,branch_id,product_id) REFERENCES inventory(tenant_id,branch_id,product_id),
 CHECK(disposition<>'RESTOCK' OR received_condition='SELLABLE')
);
CREATE INDEX ix_branch_return_queue ON pos_returns(tenant_id,branch_id,state,created_at,id);
CREATE INDEX ix_original_return_items ON pos_return_items(tenant_id,order_id,product_id);
