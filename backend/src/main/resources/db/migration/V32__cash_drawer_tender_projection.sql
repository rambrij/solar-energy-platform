CREATE TABLE pos_drawer_tenders (
 id VARCHAR(80) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 branch_id BIGINT NOT NULL,
 session_id VARCHAR(36) NOT NULL,
 order_reference VARCHAR(36) NOT NULL,
 cash_received DECIMAL(19,2) NOT NULL,
 change_given DECIMAL(19,2) NOT NULL,
 FOREIGN KEY (tenant_id) REFERENCES tenants(id),
 CHECK (cash_received>=0 AND change_given>=0 AND change_given<=cash_received)
);
CREATE INDEX pos_drawer_tenders_session ON pos_drawer_tenders(tenant_id,branch_id,session_id);
INSERT INTO pos_drawer_tenders(id,tenant_id,branch_id,session_id,order_reference,cash_received,change_given)
 SELECT a.id,o.tenant_id,o.branch_id,o.session_id,o.id,a.cash_received,a.change_given
 FROM vendor_payment_attempts a JOIN orders o ON o.tenant_id=a.tenant_id AND o.id=a.order_id
 WHERE o.sales_channel='IN_STORE' AND a.method='CASH' AND a.state='CONFIRMED';
INSERT INTO pos_drawer_tenders(id,tenant_id,branch_id,session_id,order_reference,cash_received,change_given)
 SELECT CONCAT('legacy:',o.id),o.tenant_id,o.branch_id,o.session_id,o.id,o.cash_received,o.change_given
 FROM orders o WHERE o.sales_channel='IN_STORE' AND o.status='PAID' AND o.cash_received>0
 AND NOT EXISTS(SELECT 1 FROM vendor_payment_attempts a WHERE a.tenant_id=o.tenant_id AND a.order_id=o.id AND a.method='CASH' AND a.state='CONFIRMED');
