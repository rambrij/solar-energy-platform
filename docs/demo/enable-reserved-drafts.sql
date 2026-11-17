-- Optional existing local demo upgrade after Flyway V8. Never use as production provisioning.
USE solar;
UPDATE tenants t SET online_branch_id = 900001
WHERE t.id = 900001 AND EXISTS (SELECT 1 FROM branches b WHERE b.tenant_id = t.id AND b.id = 900001);
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,'POS_SALE_CANCEL','local-cashier'
FROM vendor_roles r
WHERE r.tenant_id=900001 AND r.role_key='local-cashier'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission='POS_SALE_CANCEL');
