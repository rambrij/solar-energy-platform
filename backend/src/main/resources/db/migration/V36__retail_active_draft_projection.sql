CREATE TABLE pos_active_drafts (
 tenant_id BIGINT NOT NULL,
 branch_id BIGINT NOT NULL,
 cashier VARCHAR(100) NOT NULL,
 session_id VARCHAR(36) NOT NULL,
 order_reference VARCHAR(36) NOT NULL,
 PRIMARY KEY(tenant_id,order_reference),
 FOREIGN KEY(tenant_id,branch_id,cashier,session_id) REFERENCES pos_sessions(tenant_id,branch_id,cashier,id)
);
CREATE INDEX pos_active_drafts_session ON pos_active_drafts(tenant_id,branch_id,cashier,session_id);
INSERT INTO pos_active_drafts(tenant_id,branch_id,cashier,session_id,order_reference)
 SELECT tenant_id,branch_id,cashier,session_id,id FROM orders WHERE sales_channel='IN_STORE' AND status='DRAFT';
