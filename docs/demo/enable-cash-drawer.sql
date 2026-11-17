-- Optional local demo upgrade. Run after the demo cashier already exists.
-- Adds only cash drawer permissions to the existing cashier role.
USE solar;
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-cashier'
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_CASH_IN' permission UNION ALL SELECT 'POS_CASH_OUT' UNION ALL SELECT 'POS_SAFE_DROP') p
WHERE r.tenant_id=900001 AND r.role_key='local-cashier'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
