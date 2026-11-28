INSERT INTO vendor_permission_catalogue(permission) VALUES ('INVOICE_VIEW');

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'INVOICE_VIEW',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id
 AND existing.role_id=r.id
 AND existing.permission='INVOICE_VIEW'
WHERE (r.role_key IN ('standard:VENDOR_ADMIN','standard:ACCOUNTANT')
       OR (r.template_key IN ('VENDOR_ADMIN','ACCOUNTANT') AND r.template_version=1))
  AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events
  (tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.updated_by,'ROLE',CAST(r.id AS CHAR),'INVOICE_VIEW_PERMISSION_MIGRATED',
       'Read-only tenant invoice register permission added to Vendor Admin and Accountant templates'
FROM vendor_roles r
WHERE r.role_key IN ('standard:VENDOR_ADMIN','standard:ACCOUNTANT')
   OR (r.template_key IN ('VENDOR_ADMIN','ACCOUNTANT') AND r.template_version=1);
