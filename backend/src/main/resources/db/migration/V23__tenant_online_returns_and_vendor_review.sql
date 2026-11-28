-- Tenant-scoped online return requests. No refund, carrier label, or stock movement.
CREATE TABLE online_return_policies (
 tenant_id BIGINT NOT NULL PRIMARY KEY, return_window_days INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, updated_by VARCHAR(100) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_online_return_policy_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 CONSTRAINT fk_online_return_policy_actor FOREIGN KEY(tenant_id,updated_by) REFERENCES employees(tenant_id,username),
 CONSTRAINT chk_online_return_window CHECK(return_window_days BETWEEN 0 AND 365)
);
CREATE TABLE online_return_policy_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, actor_username VARCHAR(100) NOT NULL,
 before_days INT NOT NULL, after_days INT NOT NULL, reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 KEY ix_online_return_policy_events_tenant(tenant_id,occurred_at,id),
 CONSTRAINT fk_online_return_policy_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 CONSTRAINT fk_online_return_policy_event_actor FOREIGN KEY(tenant_id,actor_username) REFERENCES employees(tenant_id,username),
 CONSTRAINT chk_online_return_policy_event_days CHECK(before_days BETWEEN 0 AND 365 AND after_days BETWEEN 0 AND 365)
);
CREATE TABLE online_returns (
 id VARCHAR(36) NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, order_id VARCHAR(36) NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL, request_hash VARCHAR(64) NOT NULL, reason VARCHAR(250) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED', reviewed_by VARCHAR(100) NULL, review_reason VARCHAR(500) NULL,
 customer_message VARCHAR(500) NULL, created_at TIMESTAMP(6) NOT NULL, reviewed_at TIMESTAMP(6) NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_online_return_tenant_id(tenant_id,id), UNIQUE KEY uk_online_return_idempotency(tenant_id,customer_id,idempotency_key),
 KEY ix_online_return_order(tenant_id,order_id,status),
 CONSTRAINT fk_online_return_order FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id),
 CONSTRAINT fk_online_return_customer FOREIGN KEY(customer_id) REFERENCES customer_profiles(id),
 CONSTRAINT fk_online_return_reviewer FOREIGN KEY(tenant_id,reviewed_by) REFERENCES employees(tenant_id,username),
 CONSTRAINT chk_online_return_status CHECK(status IN ('REQUESTED','APPROVED','REJECTED')),
 CONSTRAINT chk_online_return_review CHECK((status='REQUESTED' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (status<>'REQUESTED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL AND customer_message IS NOT NULL))
);
CREATE TABLE online_return_items (
 tenant_id BIGINT NOT NULL, return_id VARCHAR(36) NOT NULL, order_id VARCHAR(36) NOT NULL,
 product_id BIGINT NOT NULL, product_name VARCHAR(160) NOT NULL, quantity INT NOT NULL,
 PRIMARY KEY(tenant_id,return_id,product_id),
 CONSTRAINT fk_online_return_item_return FOREIGN KEY(tenant_id,return_id) REFERENCES online_returns(tenant_id,id),
 CONSTRAINT fk_online_return_item_order_line FOREIGN KEY(tenant_id,order_id,product_id) REFERENCES order_items(tenant_id,order_id,product_id),
 CONSTRAINT chk_online_return_item_quantity CHECK(quantity BETWEEN 1 AND 10000)
);
CREATE TABLE online_return_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, return_id VARCHAR(36) NOT NULL,
 actor_type VARCHAR(12) NOT NULL, actor_id VARCHAR(100) NOT NULL, event_type VARCHAR(24) NOT NULL,
 customer_message VARCHAR(500) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 KEY ix_online_return_events_history(tenant_id,return_id,occurred_at,id),
 CONSTRAINT fk_online_return_event_return FOREIGN KEY(tenant_id,return_id) REFERENCES online_returns(tenant_id,id),
 CONSTRAINT chk_online_return_event_actor CHECK(actor_type IN('CUSTOMER','VENDOR')),
 CONSTRAINT chk_online_return_event_type CHECK(event_type IN('REQUESTED','APPROVED','REJECTED'))
);
/*!80000 CREATE TRIGGER trg_online_return_events_no_update BEFORE UPDATE ON online_return_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Online return events are immutable' */;
/*!80000 CREATE TRIGGER trg_online_return_events_no_delete BEFORE DELETE ON online_return_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Online return events are immutable' */;
/*!80000 CREATE TRIGGER trg_online_return_policy_events_no_update BEFORE UPDATE ON online_return_policy_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Online return policy events are immutable' */;
/*!80000 CREATE TRIGGER trg_online_return_policy_events_no_delete BEFORE DELETE ON online_return_policy_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Online return policy events are immutable' */;
INSERT INTO vendor_permission_catalogue(permission) VALUES('RETURN_VIEW'),('RETURN_APPROVE');
-- Vendor Admin can configure/review; Inventory Manager receives read-only queue visibility.
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'RETURN_VIEW',r.updated_by FROM vendor_roles r
LEFT JOIN vendor_role_permissions x ON x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission='RETURN_VIEW'
WHERE (r.role_key IN('standard:VENDOR_ADMIN','standard:INVENTORY_MANAGER') OR (r.template_key IN('VENDOR_ADMIN','INVENTORY_MANAGER') AND r.template_version=1))
AND x.permission IS NULL;
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'RETURN_APPROVE',r.updated_by FROM vendor_roles r
LEFT JOIN vendor_role_permissions x ON x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission='RETURN_APPROVE'
WHERE (r.role_key='standard:VENDOR_ADMIN' OR (r.template_key='VENDOR_ADMIN' AND r.template_version=1))
AND x.permission IS NULL;
INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.updated_by,'ROLE',CAST(r.id AS CHAR),'ONLINE_RETURN_PERMISSION_MIGRATED','Online return access enabled for tenant operations'
FROM vendor_roles r
WHERE r.role_key IN('standard:VENDOR_ADMIN','standard:INVENTORY_MANAGER') OR (r.template_key IN('VENDOR_ADMIN','INVENTORY_MANAGER') AND r.template_version=1);
