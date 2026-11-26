INSERT INTO vendor_permission_catalogue(permission) VALUES ('LEAD_VIEW');

CREATE TABLE marketplace_enquiries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  customer_id BIGINT NULL,
  product_id BIGINT NULL,
  product_name_snapshot VARCHAR(160) NULL,
  contact_name VARCHAR(120) NOT NULL,
  contact_email VARCHAR(254) NOT NULL,
  contact_phone VARCHAR(30) NULL,
  message VARCHAR(2000) NOT NULL,
  consent_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  requester_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_marketplace_enquiry_tenant_id (tenant_id,id),
  KEY ix_marketplace_enquiry_tenant_created (tenant_id,created_at,id),
  KEY ix_marketplace_enquiry_rate_limit (tenant_id,requester_hash,created_at),
  CONSTRAINT fk_marketplace_enquiry_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_marketplace_enquiry_customer FOREIGN KEY (tenant_id,customer_id)
    REFERENCES tenant_customers(tenant_id,customer_id),
  CONSTRAINT fk_marketplace_enquiry_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT chk_marketplace_enquiry_status CHECK (status IN ('NEW'))
);

INSERT INTO vendor_role_permissions (tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'LEAD_VIEW',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission='LEAD_VIEW'
WHERE r.template_key IN ('VENDOR_ADMIN','SALES_MANAGER')
  AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events
  (tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.updated_by,'ROLE_PERMISSION',CAST(r.id AS CHAR),
  'LEAD_VIEW_PERMISSION_MIGRATED','Tenant-scoped marketplace lead inbox permission added to supported sales roles'
FROM vendor_roles r
WHERE r.template_key IN ('VENDOR_ADMIN','SALES_MANAGER');
