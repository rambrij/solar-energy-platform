-- Activate the already-approved SRS inventory transfer permission for warehouse operations.
INSERT INTO vendor_permission_catalogue(permission) VALUES ('INVENTORY_TRANSFER');

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'INVENTORY_TRANSFER',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id
 AND existing.role_id=r.id
 AND existing.permission='INVENTORY_TRANSFER'
WHERE (r.role_key IN ('standard:VENDOR_ADMIN','standard:INVENTORY_MANAGER')
       OR (r.template_key IN ('VENDOR_ADMIN','INVENTORY_MANAGER') AND r.template_version=1))
  AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events
  (tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.updated_by,'ROLE',CAST(r.id AS CHAR),'INVENTORY_TRANSFER_PERMISSION_MIGRATED',
       'SRS inventory transfer permission enabled for Vendor Admin and Inventory Manager warehouse operations'
FROM vendor_roles r
WHERE r.role_key IN ('standard:VENDOR_ADMIN','standard:INVENTORY_MANAGER')
   OR (r.template_key IN ('VENDOR_ADMIN','INVENTORY_MANAGER') AND r.template_version=1);
