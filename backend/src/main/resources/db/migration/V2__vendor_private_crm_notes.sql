INSERT INTO vendor_permission_catalogue (permission) VALUES
  ('CUSTOMER_RELATION_VIEW'), ('CRM_NOTE_VIEW'), ('CRM_NOTE_CREATE'), ('CRM_NOTE_UPDATE');

CREATE TABLE vendor_customer_crm_notes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  tenant_customer_id VARCHAR(36) NOT NULL,
  note_text VARCHAR(2000) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vendor_customer_crm_note_tenant_id (tenant_id, id),
  KEY ix_vendor_customer_crm_note_customer (tenant_id, tenant_customer_id, created_at, id),
  CONSTRAINT fk_vendor_crm_note_relationship FOREIGN KEY (tenant_id, tenant_customer_id)
    REFERENCES tenant_customers (tenant_id, id),
  CONSTRAINT fk_vendor_crm_note_created_by FOREIGN KEY (tenant_id, created_by)
    REFERENCES employees (tenant_id, username),
  CONSTRAINT fk_vendor_crm_note_updated_by FOREIGN KEY (tenant_id, updated_by)
    REFERENCES employees (tenant_id, username),
  CONSTRAINT chk_vendor_crm_note_nonblank CHECK (CHAR_LENGTH(TRIM(note_text)) > 0)
);

INSERT INTO vendor_role_permissions (tenant_id, role_id, permission, created_by)
SELECT r.tenant_id, r.id, p.permission, r.updated_by
FROM vendor_roles r
CROSS JOIN (
  SELECT 'CUSTOMER_RELATION_VIEW' AS permission
  UNION ALL SELECT 'CRM_NOTE_VIEW'
  UNION ALL SELECT 'CRM_NOTE_CREATE'
  UNION ALL SELECT 'CRM_NOTE_UPDATE'
) p
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id = r.tenant_id
 AND existing.role_id = r.id
 AND existing.permission = p.permission
WHERE r.template_key = 'VENDOR_ADMIN'
  AND r.template_version = 1
  AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events
  (tenant_id, actor_username, resource_type, resource_id, action, reason)
SELECT r.tenant_id, r.updated_by, 'ROLE_PERMISSION', CAST(r.id AS CHAR),
       'CRM_PERMISSION_MIGRATED',
       'Vendor-private CRM permissions added to the standard Vendor Admin role'
FROM vendor_roles r
WHERE r.template_key = 'VENDOR_ADMIN'
  AND r.template_version = 1;
