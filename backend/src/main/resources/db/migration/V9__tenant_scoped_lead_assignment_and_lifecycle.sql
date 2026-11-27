INSERT INTO vendor_permission_catalogue(permission) VALUES ('LEAD_UPDATE'),('LEAD_ASSIGN');

ALTER TABLE marketplace_enquiries
  ADD COLUMN lead_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  ADD COLUMN assigned_to VARCHAR(100) NULL,
  ADD COLUMN updated_by VARCHAR(100) NULL,
  ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD KEY ix_marketplace_enquiry_assignee (tenant_id,assigned_to,created_at,id),
  ADD CONSTRAINT fk_marketplace_enquiry_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  ADD CONSTRAINT fk_marketplace_enquiry_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  ADD CONSTRAINT chk_marketplace_lead_status CHECK (lead_status IN ('NEW','CONTACTED','QUALIFIED','LOST'));

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,r.updated_by
FROM vendor_roles r
JOIN (SELECT 'LEAD_UPDATE' permission UNION ALL SELECT 'LEAD_ASSIGN') p
LEFT JOIN vendor_role_permissions existing
 ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission=p.permission
WHERE r.template_key IN ('VENDOR_ADMIN','SALES_MANAGER') AND existing.permission IS NULL;

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'LEAD_VIEW',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing
 ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission='LEAD_VIEW'
WHERE r.template_key='SALES_EXECUTIVE' AND existing.permission IS NULL;
